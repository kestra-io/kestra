import type {I18n} from "vue-i18n"
import {describe, test, expect, vi, afterEach} from "vitest"
import {
    designSystemLocale,
    registerDesignSystemI18n,
    setDesignSystemLocale,
} from "../../../src/i18n"

vi.mock("../../../src/components/Data/KsEmpty.locale.ts", () => ({
    default: {},
}))

const localeModules = import.meta.glob<{
    default: Record<string, object>
}>("../../../src/components/**/*.locale.ts")

describe("design-system i18n", () => {
    afterEach(() => {
        setDesignSystemLocale("en")
    })

    test("starts with English as the default locale", () => {
        expect(designSystemLocale.value).toBe("en")
    })

    test("updates the locale through the shared ref", async () => {
        const {designSystemLocale: importedDesignSystemLocale} = await import("../../../src/i18n")

        setDesignSystemLocale("fr")

        expect(designSystemLocale.value).toBe("fr")
        expect(importedDesignSystemLocale.value).toBe("fr")
        expect(importedDesignSystemLocale).toBe(designSystemLocale)
    })

    test("merges messages once for every language found", async () => {
        const modules = await Promise.all(
            Object.values(localeModules).map((loadModule) => loadModule()),
        )
        const languageCount = modules.reduce(
            (count, module) => count + Object.keys(module.default).length,
            0,
        )
        const mergeLocaleMessage = vi.fn()
        const i18n = {
            global: {
                mergeLocaleMessage,
            },
        } as unknown as I18n

        await registerDesignSystemI18n(i18n)

        expect(mergeLocaleMessage).toHaveBeenCalledTimes(languageCount)
    })

    test("merges messages into the provided i18n instance", async () => {
        const modules = await Promise.all(
            Object.values(localeModules).map((loadModule) => loadModule()),
        )
        const expectedMessages = modules.flatMap((module) => Object.entries(module.default))
        const mergeLocaleMessage = vi.fn()
        const i18n = {
            global: {
                mergeLocaleMessage,
            },
        } as unknown as I18n

        await registerDesignSystemI18n(i18n)

        expect(mergeLocaleMessage.mock.calls).toEqual(expectedMessages)
    })

    test("registers English messages", async () => {
        const mergeLocaleMessage = vi.fn()
        const i18n = {
            global: {
                mergeLocaleMessage,
            },
        } as unknown as I18n

        await registerDesignSystemI18n(i18n)

        expect(mergeLocaleMessage).toHaveBeenCalledWith(
            "en",
            expect.any(Object),
        )
    })

    test("resolves when a locale module contributes nothing", async () => {
        const mergeLocaleMessage = vi.fn()
        const i18n = {
            global: {
                mergeLocaleMessage,
            },
        } as unknown as I18n

        await expect(registerDesignSystemI18n(i18n)).resolves.toBeUndefined()
    })
})
