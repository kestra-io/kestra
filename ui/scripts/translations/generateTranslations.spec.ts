import {describe, expect, it} from "vitest"
import {mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {join} from "node:path"

import {generateTranslations} from "./generateTranslations.ts"
import type {TranslationClient} from "./generateTranslations.ts"

const clientReturning = (text: string) =>
    ({models: {generateContent: async () => ({text})}}) as unknown as TranslationClient

describe("generateTranslations", () => {
    it("writes a language file a tenant type's folder does not have yet", async () => {
        const dir = mkdtempSync(join(tmpdir(), "translations-"))
        writeFileSync(join(dir, "en.json"), JSON.stringify({en: {greeting: "Hello"}}))

        try {
            await generateTranslations({
                client: clientReturning("Bonjour"),
                translationsDir: dir,
                languages: [["fr", "French"]],
            })

            expect(readdirSync(dir).sort()).toEqual(["en.json", "fr.json"])
            expect(JSON.parse(readFileSync(join(dir, "fr.json"), "utf-8"))).toEqual({fr: {greeting: "Bonjour"}})
        } finally {
            rmSync(dir, {recursive: true, force: true})
        }
    })
})
