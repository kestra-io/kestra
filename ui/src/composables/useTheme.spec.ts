import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {nextTick, ref} from "vue"

let mockTheme = ref("light")
let currentReportedTheme: "light" | "dark" = "light"

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({
        get theme() {
            return mockTheme.value
        },
        set theme(val: string) {
            mockTheme.value = val
        },
    }),
}))

vi.mock("../utils/utils", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../utils/utils")>()),
    getTheme: () => currentReportedTheme,
}))

import {storageKeys} from "../utils/constants"
import {editorDarkTheme, editorLightTheme, useCodeTheme, useTheme} from "./useTheme"

describe("useTheme", () => {
    beforeEach(() => {
        mockTheme.value = "light"
        currentReportedTheme = "light"
    })

    it("matches what getTheme() reports", () => {
        currentReportedTheme = "dark"
        const theme = useTheme()
        expect(theme.value).toBe("dark")
    })

    it("re-evaluates when miscStore.theme changes to track theme switches", () => {
        const theme = useTheme()
        expect(theme.value).toBe("light")

        currentReportedTheme = "dark"
        mockTheme.value = "dark"

        expect(theme.value).toBe("dark")
    })

    it("re-evaluates when the OS theme changes while miscStore.theme stays syncWithSystem", () => {
        const osListeners = new Set<(event: {matches: boolean}) => void>()
        vi.stubGlobal("matchMedia", (query: string) => ({
            media: query,
            matches: false,
            addEventListener: (_type: string, listener: (event: {matches: boolean}) => void) => osListeners.add(listener),
            removeEventListener: (_type: string, listener: (event: {matches: boolean}) => void) => osListeners.delete(listener),
        }))
        mockTheme.value = "syncWithSystem"
        const theme = useTheme()
        expect(theme.value).toBe("light")

        currentReportedTheme = "dark"
        osListeners.forEach((listener) => listener({matches: true}))

        expect(theme.value).toBe("dark")
        vi.unstubAllGlobals()
    })
})

describe("useCodeTheme", () => {
    beforeEach(() => {
        mockTheme.value = "light"
        currentReportedTheme = "light"
        editorDarkTheme.value = null
        editorLightTheme.value = null
        localStorage.removeItem(storageKeys.THEME)
    })

    afterEach(async () => {
        editorDarkTheme.value = null
        editorLightTheme.value = null
        await nextTick()
        localStorage.removeItem(storageKeys.THEME)
    })

    function switchApp(theme: "light" | "dark" | "dark-2") {
        localStorage.setItem(storageKeys.THEME, theme)
        currentReportedTheme = theme === "light" ? "light" : "dark"
        mockTheme.value = theme
    }

    it("uses Kestra Light and Kestra Dark until a theme is picked for the mode", () => {
        const codeTheme = useCodeTheme()
        expect(codeTheme.value).toBe("kestra-light")

        switchApp("dark-2")
        expect(codeTheme.value).toBe("kestra-dark")

        switchApp("dark")
        expect(codeTheme.value).toBe("kestra-dark")
    })

    it("uses the theme picked for the current mode and switches as soon as it changes", () => {
        const codeTheme = useCodeTheme()
        editorLightTheme.value = "kestra-light-high-contrast"
        editorDarkTheme.value = "kestra-midnight"
        expect(codeTheme.value).toBe("kestra-light-high-contrast")

        switchApp("dark-2")
        expect(codeTheme.value).toBe("kestra-midnight")

        editorDarkTheme.value = null
        expect(codeTheme.value).toBe("kestra-dark")
    })

    it("ignores a stored theme that is unknown or made for the other mode", () => {
        switchApp("dark")
        const codeTheme = useCodeTheme()

        editorDarkTheme.value = "kestra-light"
        expect(codeTheme.value).toBe("kestra-dark")

        editorDarkTheme.value = "a-theme-that-was-removed"
        expect(codeTheme.value).toBe("kestra-dark")
    })
})
