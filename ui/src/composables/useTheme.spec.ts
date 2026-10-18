import {describe, it, expect, vi, beforeEach} from "vitest"

const currentTheme = vi.hoisted(() => ({value: "light" as "light" | "dark"}))

vi.mock("../utils/utils", async (importOriginal) => {
    const original = await importOriginal<Record<string, unknown>>()
    return {
        ...original,
        getTheme: () => currentTheme.value,
    }
})

vi.mock("override/stores/misc", async () => {
    const {ref} = await import("vue")
    const theme = ref("light")
    return {
        useMiscStore: () => ({
            get theme() {
                return theme.value
            },
            set theme(val: string) {
                theme.value = val
            },
        }),
    }
})

import {useTheme} from "./useTheme"
import {useMiscStore} from "override/stores/misc"
import {getTheme} from "../utils/utils"

describe("useTheme", () => {
    beforeEach(() => {
        currentTheme.value = "light"
        useMiscStore().theme = "light"
    })

    it("returns the current theme", () => {
        const theme = useTheme()
        expect(theme.value).toBe("light")
    })

    it("matches what getTheme() reports", () => {
        const theme = useTheme()
        expect(theme.value).toBe(getTheme())

        currentTheme.value = "dark"
        useMiscStore().theme = "dark"
        expect(theme.value).toBe(getTheme())
    })

    it("re-evaluates when miscStore.theme changes so the value tracks a theme switch", () => {
        const theme = useTheme()
        expect(theme.value).toBe("light")

        currentTheme.value = "dark"
        useMiscStore().theme = "dark"
        expect(theme.value).toBe("dark")
    })

    it("observes the same value across two callers", () => {
        const firstCaller = useTheme()
        const secondCaller = useTheme()

        expect(firstCaller.value).toBe("light")
        expect(secondCaller.value).toBe("light")

        currentTheme.value = "dark"
        useMiscStore().theme = "dark"

        expect(firstCaller.value).toBe("dark")
        expect(secondCaller.value).toBe("dark")
    })
})
