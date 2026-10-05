import type {I18n} from "vue-i18n"
import {describe, test, expect, vi, afterEach} from "vitest"
import {
    designSystemLocale,
    registerDesignSystemI18n,
    setDesignSystemLocale,
} from "../../../src/i18n"

const EMPTY_LOCALE_MODULE = "../../../src/components/Data/KsEmpty.locale.ts"

const localeModules = import.meta.glob<{
    default: Record<string, object>
}>("../../../src/components/**/*.locale.ts")

function stubI18n() {
    const mergeLocaleMessage = vi.fn()
    return {mergeLocaleMessage, i18n: {global: {mergeLocaleMessage}} as unknown as I18n}
}

async function localeEntries(skippedModule?: string) {
    const modules = await Promise.all(
        Object.entries(localeModules)
            .filter(([path]) => path !== skippedModule)
            .map(([, loadModule]) => loadModule()),
    )
    return modules.flatMap((module) => Object.entries(module.default))
}

describe("design-system i18n", () => {
    afterEach(() => {
        setDesignSystemLocale("en")
        vi.doUnmock(EMPTY_LOCALE_MODULE)
        vi.resetModules()
    })

    test("starts with English as the default locale", () => {
        expect(designSystemLocale.value).toBe("en")
    })

    test("updates the locale through setDesignSystemLocale", () => {
        setDesignSystemLocale("fr")

        expect(designSystemLocale.value).toBe("fr")
    })

    test("merges every locale module into the provided i18n instance", async () => {
        const {i18n, mergeLocaleMessage} = stubI18n()

        await registerDesignSystemI18n(i18n)

        expect(mergeLocaleMessage.mock.calls).toEqual(await localeEntries())
    })

    test("registers English messages", async () => {
        const {i18n, mergeLocaleMessage} = stubI18n()

        await registerDesignSystemI18n(i18n)

        expect(mergeLocaleMessage).toHaveBeenCalledWith(
            "en",
            expect.objectContaining({no_data: expect.any(String)}),
        )
    })

    test("still merges the other modules when one contributes nothing", async () => {
        const expected = await localeEntries(EMPTY_LOCALE_MODULE)
        vi.doMock(EMPTY_LOCALE_MODULE, () => ({default: {}}))
        vi.resetModules()
        const {registerDesignSystemI18n: register} = await import("../../../src/i18n")
        const {i18n, mergeLocaleMessage} = stubI18n()

        await register(i18n)

        expect(mergeLocaleMessage.mock.calls).toEqual(expected)
    })
})
