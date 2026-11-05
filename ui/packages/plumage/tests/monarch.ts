import {existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {dirname, join} from "node:path"
import {fileURLToPath, pathToFileURL} from "node:url"

export interface MonarchLanguage {
    tokenPostfix?: string
    defaultToken?: string
    brackets?: ({token?: string} | string[])[]
    tokenizer?: Record<string, unknown[]>
}

const DEFINITIONS = join(dirname(fileURLToPath(import.meta.resolve("monaco-editor/languages/definitions/yaml/yaml.js"))), "..")

/**
 * The Monarch definitions bundled with monaco-editor, keyed by language. The definition files import Monaco's
 * DOM-bound editor API for one enum, so they are copied with that import replaced before loading them in Node.
 */
export async function monarchLanguages(): Promise<Map<string, MonarchLanguage[]>> {
    const dir = mkdtempSync(join(tmpdir(), "monarch-"))
    const names = readdirSync(DEFINITIONS).filter(name => existsSync(join(DEFINITIONS, name, `${name}.js`)))
    for (const name of names) {
        const source = readFileSync(join(DEFINITIONS, name, `${name}.js`), "utf8")
            .replace(/^import \{ languages \} from '[^']+';/m, "const languages = {IndentAction: {None: 0, Indent: 1, IndentOutdent: 2, Outdent: 3}};")
            .replace(/'\.\.\/(\w+)\/\1\.js'/g, "'./$1.mjs'")
        writeFileSync(join(dir, `${name}.mjs`), source)
    }
    const result = new Map<string, MonarchLanguage[]>()
    for (const name of names) {
        const module = await import(pathToFileURL(join(dir, `${name}.mjs`)).href) as Record<string, unknown>
        const languages = Object.values(module)
            .map(value => (value as {language?: MonarchLanguage})?.language ?? value as MonarchLanguage)
            .filter(language => language?.tokenizer)
        if (languages.length) result.set(name, languages)
    }
    rmSync(dir, {recursive: true, force: true})
    return result
}

/** Every token type a Monaco grammar can emit, keyed by language. */
export async function monarchTokens(): Promise<Map<string, Set<string>>> {
    const result = new Map<string, Set<string>>()
    for (const [name, languages] of await monarchLanguages()) {
        const tokens = new Set<string>()
        languages.forEach(language => collect(name, language, tokens))
        result.set(name, tokens)
    }
    return result
}

function collect(name: string, language: MonarchLanguage, tokens: Set<string>) {
    const postfix = language.tokenPostfix ?? `.${name}`
    const brackets = (language.brackets ?? []).map(b => Array.isArray(b) ? b[2] : b.token).filter(Boolean) as string[]
    const add = (token: unknown) => {
        if (typeof token !== "string" || token === "" || /^\$S?\d+$/.test(token)) return
        if (token === "@brackets") brackets.forEach(b => tokens.add(b + postfix))
        else if (!token.startsWith("@")) tokens.add(token.replace(/\$S?\d+/g, "dynamic") + postfix)
    }
    const walk = (action: unknown): void => {
        if (typeof action === "string") return add(action)
        if (Array.isArray(action)) return action.forEach(walk)
        if (action && typeof action === "object") {
            if ("token" in action) add(action.token)
            if ("cases" in action && action.cases) Object.values(action.cases).forEach(walk)
        }
    }
    for (const rules of Object.values(language.tokenizer ?? {})) {
        for (const rule of rules) {
            if (Array.isArray(rule)) walk(rule[1])
            else if (rule && typeof rule === "object" && !("include" in rule)) walk("action" in rule ? rule.action : rule)
        }
    }
    add(language.defaultToken)
}
