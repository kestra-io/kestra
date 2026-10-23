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

vi.mock("../utils/utils", () => ({
    getTheme: () => currentReportedTheme,
}))

import {useTheme} from "./useTheme"

describe("useTheme", () => {
    beforeEach(() => {
        mockTheme.value = "light"
        currentReportedTheme = "light"
    })

    it("returns the current theme", () => {
        currentReportedTheme = "light"
        const theme = useTheme()
        expect(theme.value).toBe("light")
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

    it("two callers observe the same value", () => {
        const theme1 = useTheme()
        const theme2 = useTheme()

        expect(theme1.value).toBe(theme2.value)

        currentReportedTheme = "dark"
        mockTheme.value = "dark"

        expect(theme1.value).toBe("dark")
        expect(theme2.value).toBe("dark")
    })
})
