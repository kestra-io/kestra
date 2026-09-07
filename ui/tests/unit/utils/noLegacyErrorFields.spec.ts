import {describe, expect, it} from "vitest"
import {globSync, readFileSync} from "node:fs"
import {join, relative, resolve} from "node:path"

/**
 * `message`, `_links`, `_embedded.errors[]` and `invalids[]` are not part of the API's error format. Reading
 * one yields `undefined`, which renders as a blank toast rather than failing loudly.
 *
 * The type system cannot catch this alone: a `catch (e: any)` call site defeats it, and `any` creeps back
 * easily, so this scan is the real guard. It also blocks the prose-matching coupling from returning — a
 * `data.message` read is the first step back towards `message.includes("already exists")`.
 *
 * If a match is legitimate, add it to ALLOWED with a one-line reason rather than loosening the pattern.
 */
const ROOT = resolve(__dirname, "../../..")

const SCANNED_DIRS = ["src", "packages"]

const FORBIDDEN = [
    {pattern: /\b_embedded\b/, what: "_embedded.errors[] — use problem.errors[]"},
    {pattern: /\b_links\b/, what: "_links — removed, no client ever read it"},
    {pattern: /\.invalids\b/, what: "invalids[] — use problem.errors[]"},
    {pattern: /\bresponse\s*\??\.\s*data\s*\??\.\s*message\b/, what: "response.data.message — use asProblem(err)?.detail"},
    {pattern: /\bdata\s*\?\.\s*message\b/, what: "data?.message — use asProblem(err)?.detail"},
    {pattern: /content\s*\?\.\s*message\b/, what: "content?.message — the toast takes a problem document"},
]

/** Paths where a match is expected and harmless. */
const ALLOWED: Array<{file: string; reason: string}> = [
    // Generated SDK surface: mirrors the OpenAPI spec, not hand-written error handling.
    {file: "packages/kestra-sdk/src/openapi", reason: "generated from the OpenAPI spec"},
    // The comments that explain this very rule.
    {file: "src/utils/kestraHttp.ts", reason: "documents the removed fields"},
    {file: "packages/hey-api-plugin/src/problem.ts", reason: "documents the removed fields"},
]

function sourceFiles(): string[] {
    return SCANNED_DIRS.flatMap((dir) =>
        globSync(`${dir}/**/*.{ts,vue,js}`, {cwd: ROOT})
            .filter((file) => !file.includes("node_modules") && !file.includes("/dist/")),
    )
}

function isAllowed(file: string): boolean {
    return ALLOWED.some((entry) => file.startsWith(entry.file))
}

describe("no legacy error fields", () => {
    it("finds source files to scan, so a broken glob cannot make this test vacuous", () => {
        expect(sourceFiles().length).toBeGreaterThan(500)
    })

    it.each(FORBIDDEN)("does not read $what", ({pattern}) => {
        const offenders: string[] = []

        for (const file of sourceFiles()) {
            if (isAllowed(file)) continue
            const content = readFileSync(join(ROOT, file), "utf8")
            content.split("\n").forEach((line, index) => {
                if (pattern.test(line)) {
                    offenders.push(`${relative(ROOT, join(ROOT, file))}:${index + 1}: ${line.trim()}`)
                }
            })
        }

        expect(offenders).toEqual([])
    })
})
