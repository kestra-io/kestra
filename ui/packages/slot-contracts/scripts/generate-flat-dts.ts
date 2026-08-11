// Builds the publish-time `dist/index.d.ts` body for slot-contracts.
// Invoked from the tsdown rolldown plugin in tsdown.config.ts.
//
// The source-side `src/index.ts` keeps its `z.infer<…>` mapped-type form so
// internal monorepo consumers (resolved through Vite alias / tsconfig paths)
// see schemas-as-source-of-truth. External npm consumers receive this flat
// file instead of tsdown's noisy default emit.
//
// Discovery: this script does NOT hardcode slot names, file paths, or SDK
// imports. It reads the `slots` array literal in src/index.ts, walks each
// element's default-import declaration back to its source slot file, and
// aggregates the npm-package `import type` statements those files use so the
// printed types resolve to short names (`Task` not `import("…").Task`).

import {Project, SyntaxKind, type SourceFile, TypeFormatFlags} from "ts-morph"
import path from "node:path"
import {fileURLToPath} from "node:url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const PKG_ROOT = path.resolve(__dirname, "..")

const FORMAT =
    TypeFormatFlags.NoTruncation |
    TypeFormatFlags.UseAliasDefinedOutsideCurrentScope

// Single line `{ a: T; b: U; }` -> multi-line. Top-level only — our inferred
// shapes are flat (primitives, SDK refs, arrays), so brace/angle/paren/bracket
// depth tracking is enough.
const prettyTypeLiteral = (raw: string, baseIndent: string): string => {
    const trimmed = raw.trim().replace(/^\{\s*/, "").replace(/;?\s*\}$/, "")
    if (trimmed === "") return "{}"

    const members: string[] = []
    let depth = 0
    let current = ""
    for (const ch of trimmed) {
        if (ch === "{" || ch === "<" || ch === "(" || ch === "[") depth++
        else if (ch === "}" || ch === ">" || ch === ")" || ch === "]") depth--
        if (ch === ";" && depth === 0) {
            if (current.trim()) members.push(current.trim())
            current = ""
        } else {
            current += ch
        }
    }
    if (current.trim()) members.push(current.trim())

    const inner = members.map((m) => `${baseIndent}    ${m};`).join("\n")
    return `{\n${inner}\n${baseIndent}}`
}

// Splits a flat `{a:T; b:U; ...}` text into its top-level member names.
const memberNames = (raw: string): string[] => {
    const trimmed = raw.trim().replace(/^\{\s*/, "").replace(/;?\s*\}$/, "")
    if (trimmed === "") return []
    const names: string[] = []
    let depth = 0
    let current = ""
    for (const ch of trimmed) {
        if (ch === "{" || ch === "<" || ch === "(" || ch === "[") depth++
        else if (ch === "}" || ch === ">" || ch === ")" || ch === "]") depth--
        if (ch === ";" && depth === 0) {
            const m = current.trim()
            if (m) names.push(m.split(/[?:]/)[0].trim())
            current = ""
        } else {
            current += ch
        }
    }
    if (current.trim()) {
        names.push(current.trim().split(/[?:]/)[0].trim())
    }
    return names
}

type SlotImport = {
    /** Local default-import name in src/index.ts, e.g. "topologyDetails". */
    localName: string
    /** Module specifier as written, e.g. "./topology-details". */
    moduleSpec: string
    /** Resolved source file for the slot module. */
    sourceFile: SourceFile
}

// Reads src/index.ts and returns the discovered slot import list, in tuple
// order. Throws if `slots` isn't an `as const`-wrapped array literal of
// default-imported identifiers — keeping the codegen honest about what shape
// it can handle.
const discoverSlotImports = (indexFile: SourceFile): SlotImport[] => {
    const slotsDecl = indexFile.getVariableDeclarationOrThrow("slots")
    const init = slotsDecl.getInitializerOrThrow()
    const asExpr = init.asKind(SyntaxKind.AsExpression)
    const arrayLit = (asExpr ? asExpr.getExpression() : init).asKindOrThrow(SyntaxKind.ArrayLiteralExpression)

    return arrayLit.getElements().map((el, i) => {
        const ident = el.asKind(SyntaxKind.Identifier)
        if (!ident) {
            throw new Error(`slots[${i}] is not a bare identifier; codegen only supports default-imported slots.`)
        }
        const symbol = ident.getSymbolOrThrow()
        const importClause = symbol.getDeclarations().find((d) => d.getKind() === SyntaxKind.ImportClause)
        if (!importClause) {
            throw new Error(`slots[${i}] (${ident.getText()}) is not a default import.`)
        }
        const importDecl = importClause.getParentIfKindOrThrow(SyntaxKind.ImportDeclaration)
        const sourceFile = importDecl.getModuleSpecifierSourceFileOrThrow()
        return {
            localName: ident.getText(),
            moduleSpec: importDecl.getModuleSpecifierValue(),
            sourceFile,
        }
    })
}

// Collects every npm (non-relative) named import across the given slot files.
// `zod` is filtered out because the probe imports `z` separately.
const collectNpmImports = (files: SourceFile[]): Map<string, Set<string>> => {
    const out = new Map<string, Set<string>>()
    for (const file of files) {
        for (const imp of file.getImportDeclarations()) {
            const mod = imp.getModuleSpecifierValue()
            if (mod.startsWith(".") || mod === "zod") continue
            const set = out.get(mod) ?? new Set<string>()
            for (const ni of imp.getNamedImports()) set.add(ni.getName())
            if (set.size > 0) out.set(mod, set)
        }
    }
    return out
}

// Keeps only imports actually referenced by the printed type texts, so the
// final dist/index.d.ts doesn't carry dead `import type` declarations.
const tightenImports = (
    npmImports: Map<string, Set<string>>,
    texts: string[],
): Array<{moduleSpec: string; names: string[]}> => {
    const used = new Set<string>()
    for (const t of texts) {
        for (const m of t.matchAll(/\b[A-Z]\w*/g)) used.add(m[0])
    }
    const result: Array<{moduleSpec: string; names: string[]}> = []
    for (const [moduleSpec, all] of npmImports.entries()) {
        const names = [...all].filter((n) => used.has(n)).sort()
        if (names.length > 0) result.push({moduleSpec, names})
    }
    return result.sort((a, b) => a.moduleSpec.localeCompare(b.moduleSpec))
}

export async function generateFlatDts() {
    const project = new Project({
        tsConfigFilePath: path.join(PKG_ROOT, "tsconfig.json"),
    })

    const indexFile = project.getSourceFileOrThrow(path.join(PKG_ROOT, "src/index.ts"))
    const slotImports = discoverSlotImports(indexFile)
    if (slotImports.length === 0) {
        throw new Error("No slots discovered in src/index.ts. Check the `slots` tuple.")
    }

    const npmImports = collectNpmImports(slotImports.map((s) => s.sourceFile))

    // Probe: redeclare the same slots tuple (so type lookups go through real
    // modules, not duplicated literals) and pull every npm `import type` into
    // scope so the printer uses short names.
    const probeNpmImportLines = [...npmImports.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([mod, set]) => `import type {${[...set].sort().join(", ")}} from ${JSON.stringify(mod)}`)
        .join("\n")
    const slotImportLines = slotImports
        .map(({localName, moduleSpec}) => `import ${localName} from ${JSON.stringify(moduleSpec)}`)
        .join("\n")
    const slotsArrayExpr = `[${slotImports.map((s) => s.localName).join(", ")}] as const`

    const aliasLines: string[] = []
    for (let i = 0; i < slotImports.length; i++) {
        aliasLines.push(`type __K${i} = (typeof slots)[${i}]["key"]`)
        aliasLines.push(`type __P${i} = z.infer<(typeof slots)[${i}]["propsSchema"]>`)
        aliasLines.push(`type __M${i} = z.infer<(typeof slots)[${i}]["manifestSchema"]>`)
    }
    const probeSource = `
import type {z} from "zod"
${probeNpmImportLines}
${slotImportLines}

const slots = ${slotsArrayExpr}
${aliasLines.join("\n")}
`
    const probePath = path.join(PKG_ROOT, "src/__codegen-probe.ts")
    const probeFile = project.createSourceFile(probePath, probeSource, {overwrite: true})

    const entries: Array<{key: string; propsText: string; manifestText: string}> = []
    for (let i = 0; i < slotImports.length; i++) {
        const keyAlias = probeFile.getTypeAliasOrThrow(`__K${i}`)
        const propsAlias = probeFile.getTypeAliasOrThrow(`__P${i}`)
        const manifestAlias = probeFile.getTypeAliasOrThrow(`__M${i}`)

        const key = keyAlias.getType().getLiteralValueOrThrow()
        if (typeof key !== "string") {
            throw new Error(`Slot at index ${i} has a non-string key literal.`)
        }
        entries.push({
            key,
            propsText: propsAlias.getType().getText(propsAlias, FORMAT),
            manifestText: manifestAlias.getType().getText(manifestAlias, FORMAT),
        })
    }
    probeFile.forget()

    const slotPropsBody = entries
        .map(({key, propsText}) => `    ${JSON.stringify(key)}: ${prettyTypeLiteral(propsText, "    ")};`)
        .join("\n")
    const manifestsBody = entries
        .map(({key, manifestText}) => `    ${JSON.stringify(key)}?: ${prettyTypeLiteral(manifestText, "    ")};`)
        .join("\n")
    const propNamesBody = entries
        .map(({key, propsText}) => {
            const names = memberNames(propsText)
            const union = names.map((n) => JSON.stringify(n)).join(" | ")
            return `    ${JSON.stringify(key)}: (${union})[];`
        })
        .join("\n")

    const usedImports = tightenImports(
        npmImports,
        entries.flatMap(({propsText, manifestText}) => [propsText, manifestText]),
    )
    const headerImports = usedImports
        .map(({moduleSpec, names}) => `import type {${names.join(", ")}} from ${JSON.stringify(moduleSpec)};`)
        .join("\n")

    return `// AUTO-GENERATED at build time by scripts/generate-flat-dts.ts.
// Source of truth: src/index.ts + the zod schemas it references.
${headerImports ? `\n${headerImports}\n` : ""}
export type KnownSlotProps = {
${slotPropsBody}
};

export type ManifestsRegistry = {
${manifestsBody}
};

export declare const KnownSlotsPropNames: {
${propNamesBody}
};
`
}
