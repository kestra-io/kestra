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

    it("rerolls a Slavic translation until it has the plural forms the locale rule reads", async () => {
        const dir = mkdtempSync(join(tmpdir(), "translations-"))
        writeFileSync(join(dir, "en.json"), JSON.stringify({en: {online: "no workers online | worker online | workers online"}}))
        const replies = ["нет worker'ов онлайн | worker онлайн | worker'ы онлайн", "нет worker'ов онлайн | worker онлайн | worker'а онлайн | worker'ов онлайн"]
        const prompts: string[] = []
        const client = {
            models: {
                generateContent: async ({contents}: {contents: string}) => {
                    prompts.push(contents)
                    return {text: replies.shift()}
                },
            },
        } as unknown as TranslationClient

        try {
            await generateTranslations({client, translationsDir: dir, languages: [["ru", "Russian"]]})

            expect(prompts).toHaveLength(2)
            expect(prompts[0]).toContain("Output exactly four forms")
            expect(JSON.parse(readFileSync(join(dir, "ru.json"), "utf-8"))).toEqual({
                ru: {online: "нет worker'ов онлайн | worker онлайн | worker'а онлайн | worker'ов онлайн"},
            })
        } finally {
            rmSync(dir, {recursive: true, force: true})
        }
    })
})
