import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {computed, isReactive, reactive} from "vue"
import type {editor as monacoEditor} from "monaco-editor/editor/editor.api"
import {storageKeys} from "../utils/constants"

const mocks = vi.hoisted(() => ({
    misc: undefined as unknown as {theme: string},
    plugins: {loadIcon: vi.fn()},
    flow: {flowYaml: ""},
    t: vi.fn((key: string) => key),
    router: {push: vi.fn()},
    configure: vi.fn(),
    configuratorLoaded: vi.fn(),
}))

vi.mock("override/stores/misc", () => ({useMiscStore: () => mocks.misc}))
vi.mock("../stores/plugins", () => ({usePluginsStore: () => mocks.plugins}))
vi.mock("../stores/flow", () => ({useFlowStore: () => mocks.flow}))
vi.mock("vue-i18n", async (importOriginal) => ({
    ...await importOriginal<typeof import("vue-i18n")>(),
    useI18n: () => ({t: mocks.t}),
}))
vi.mock("vue-router", async (importOriginal) => ({
    ...await importOriginal<typeof import("vue-router")>(),
    useRouter: () => mocks.router,
}))
// The factory body runs when the module is first imported, so `configuratorLoaded` counts loads.
const mockConfigurator = () => vi.doMock("./monaco/languages/languagesConfigurator", () => {
    mocks.configuratorLoaded()

    return {default: mocks.configure}
})

const editor = {} as monacoEditor.ICodeEditor

// The module registry is reset before each test and `useEditorBindings` is imported afresh, so the
// configurator's load count starts at zero and does not depend on the order of the tests.
const loadBindings = async () => {
    const {useEditorBindings} = await import("./useEditorBindings")

    return useEditorBindings()
}

const setTheme = (theme: "light" | "dark") => {
    localStorage.setItem(storageKeys.THEME, theme)
    mocks.misc.theme = theme
}

describe("useEditorBindings", () => {
    beforeEach(() => {
        vi.resetModules()
        mockConfigurator()
        mocks.misc = reactive({theme: "light"})
        mocks.configure.mockReset().mockResolvedValue(undefined)
        mocks.configuratorLoaded.mockClear()
        localStorage.setItem(storageKeys.THEME, "light")
    })

    afterEach(() => {
        localStorage.removeItem(storageKeys.THEME)
    })

    it("follows the misc store theme and re-evaluates when it changes", async () => {
        const bindings = await loadBindings()
        expect(bindings.theme).toBe("light")

        setTheme("dark")
        expect(bindings.theme).toBe("dark")

        setTheme("light")
        expect(bindings.theme).toBe("light")
    })

    it("exposes the plugins store loadIcon as loadTaskIcon", async () => {
        const bindings = await loadBindings()

        expect(bindings.loadTaskIcon).toBe(mocks.plugins.loadIcon)
    })

    it("returns a reactive object, so a consumer reading theme updates", async () => {
        const bindings = await loadBindings()
        const consumerTheme = computed(() => bindings.theme)

        expect(isReactive(bindings)).toBe(true)
        expect(consumerTheme.value).toBe("light")

        setTheme("dark")
        expect(consumerTheme.value).toBe("dark")
    })

    it("does not load the language configurator until configureLanguage is first called", async () => {
        const bindings = await loadBindings()

        expect(bindings.theme).toBe("light")
        expect(mocks.configuratorLoaded).not.toHaveBeenCalled()

        await bindings.configureLanguage(editor, "yaml", "flow")

        expect(mocks.configuratorLoaded).toHaveBeenCalledTimes(1)
    })

    it("forwards the stores, translator, editor, language, schema type and router to the configurator", async () => {
        const bindings = await loadBindings()

        await bindings.configureLanguage(editor, "yaml", "flow")

        expect(mocks.configure).toHaveBeenCalledTimes(1)
        expect(mocks.configure).toHaveBeenCalledWith(
            mocks.flow, mocks.plugins, mocks.t, editor, "yaml", "flow", mocks.router,
        )
    })

    it("passes an undefined editor and schema type through untouched", async () => {
        const bindings = await loadBindings()

        await bindings.configureLanguage(undefined, "yaml")

        expect(mocks.configure).toHaveBeenCalledWith(
            mocks.flow, mocks.plugins, mocks.t, undefined, "yaml", undefined, mocks.router,
        )
    })

    it("does not import the configurator a second time when called twice", async () => {
        const bindings = await loadBindings()

        await bindings.configureLanguage(editor, "yaml", "flow")
        await bindings.configureLanguage(editor, "yaml", "flow")

        expect(mocks.configuratorLoaded).toHaveBeenCalledTimes(1)
        expect(mocks.configure).toHaveBeenCalledTimes(2)
    })
})
