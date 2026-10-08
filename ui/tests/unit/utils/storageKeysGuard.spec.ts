import {readFileSync, readdirSync} from "node:fs"
import {dirname, join, relative, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"
import {storageKeys} from "../../../src/utils/constants"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../../src")
const STORAGE_CALL = /(?:(?:local|session)Storage\s*\.\s*(?:getItem|setItem|removeItem)|use(?:Local|Session)?Storage\s*(?:<[^()]*>)?)\(\s*(["'`])(.*?)\1/g
const DECLARED_KEYS = new Set<string>(Object.values(storageKeys))

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const path = join(dir, entry.name)
        if (entry.isDirectory()) return sources(path)
        return /\.(vue|ts)$/.test(entry.name) ? [path] : []
    })

describe("storageKeys", () => {
    it("is used wherever ui/src reads or writes a key it declares", () => {
        const offenders = sources(SRC).flatMap((path) =>
            readFileSync(path, "utf8").split("\n").flatMap((line, index) =>
                [...line.matchAll(STORAGE_CALL)]
                    .filter((match) => DECLARED_KEYS.has(match[2]))
                    .map((match) => `${relative(SRC, path)}:${index + 1} ("${match[2]}")`)))

        expect(offenders, `Use storageKeys from src/utils/constants.ts instead of the literal key:\n${offenders.join("\n")}`).toEqual([])
    })
})
