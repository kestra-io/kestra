import {readFileSync, readdirSync} from "node:fs"
import {dirname, join, relative, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../../src")
const LITERAL_KEY = /(?:(?:local|session)Storage\s*\.\s*(?:getItem|setItem|removeItem)|\buse(?:Local|Session)?Storage\s*(?:<[^()]*>)?)\(\s*(["'`])([^"'`$\n]*)\1/g

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const path = join(dir, entry.name)
        if (entry.isDirectory()) return sources(path)
        return /\.(vue|ts)$/.test(entry.name) && !/\.(spec|test|stories)\.ts$/.test(entry.name) ? [path] : []
    })

describe("storageKeys", () => {
    it("names every storage key that ui/src reads or writes", () => {
        const offenders = sources(SRC).flatMap((path) => {
            const source = readFileSync(path, "utf8")
            return [...source.matchAll(LITERAL_KEY)].map((match) =>
                `${relative(SRC, path)}:${source.slice(0, match.index).split("\n").length} ("${match[2]}")`)
        })

        expect(offenders, `Declare the key in storageKeys (src/utils/constants.ts) and use the constant:\n${offenders.join("\n")}`).toEqual([])
    })
})
