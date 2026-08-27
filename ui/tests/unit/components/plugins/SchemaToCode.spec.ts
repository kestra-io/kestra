import {describe, test, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import {loadLanguageOnDemand} from "@kestra-io/design-system/shiki"
import SchemaToCode from "../../../../src/components/plugins/schema/SchemaToCode.vue"

vi.mock("@kestra-io/design-system/shiki", () => ({
    loadLanguageOnDemand: vi.fn(),
}))

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

const loaded = ["yaml"]

const highlighter = {
    getLoadedLanguages: () => loaded,
    codeToHtml: (code: string, {lang}: {lang: string}) => {
        if (!loaded.includes(lang) && lang !== "text") {
            throw new Error(`Language \`${lang}\` not found`)
        }
        return `<pre data-lang="${lang}">${code}</pre>`
    },
}

const mountCode = (language: string) => mount(SchemaToCode, {
    props: {highlighter: highlighter as never, code: "SELECT 1", language},
    global: globalConfig,
})

describe("SchemaToCode", () => {
    beforeEach(() => {
        loaded.length = 0
        loaded.push("yaml")
        vi.mocked(loadLanguageOnDemand).mockReset()
    })

    test("highlights with a grammar the highlighter pre-registers", async () => {
        const wrapper = mountCode("yaml")
        await flushPromises()

        expect(wrapper.html()).toContain("data-lang=\"yaml\"")
        expect(loadLanguageOnDemand).not.toHaveBeenCalled()
    })

    test("fetches a grammar that is not pre-registered, then highlights with it", async () => {
        vi.mocked(loadLanguageOnDemand).mockImplementation(async () => {
            loaded.push("sql")
            return true
        })

        const wrapper = mountCode("sql")
        await flushPromises()

        expect(loadLanguageOnDemand).toHaveBeenCalledWith(highlighter, "sql")
        expect(wrapper.html()).toContain("data-lang=\"sql\"")
    })

    test("renders plain text when the grammar cannot be fetched", async () => {
        vi.mocked(loadLanguageOnDemand).mockResolvedValue(false)

        const wrapper = mountCode("nonexistent")
        await flushPromises()

        expect(wrapper.html()).toContain("data-lang=\"text\"")
    })
})
