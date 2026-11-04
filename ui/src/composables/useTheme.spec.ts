import {describe, it, expect, vi, beforeEach} from "vitest"
import {ref} from "vue"

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

import {useTheme} from "./useTheme"

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
