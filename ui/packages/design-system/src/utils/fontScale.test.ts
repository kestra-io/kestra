import {describe, expect, test} from "vitest"
import {
    APP_FONT_SIZE_KEY,
    BASE_PX,
    MONO_BASE_PX,
    type AppFontSizeMode,
} from "./fontScale"

const MODES: AppFontSizeMode[] = ["small", "medium", "large"]

describe("fontScale constants", () => {
    test("APP_FONT_SIZE_KEY equals 'appFontSize'", () => {
        expect(APP_FONT_SIZE_KEY).toBe("appFontSize")
    })

    test("BASE_PX contains an entry for every AppFontSizeMode", () => {
        expect(Object.keys(BASE_PX).sort()).toEqual([...MODES].sort())
        for (const mode of MODES) {
            expect(BASE_PX[mode]).toBeDefined()
            expect(typeof BASE_PX[mode]).toBe("number")
        }
    })

    test("MONO_BASE_PX contains the same set of keys", () => {
        expect(Object.keys(MONO_BASE_PX).sort()).toEqual([...MODES].sort())
        expect(Object.keys(MONO_BASE_PX).sort()).toEqual(Object.keys(BASE_PX).sort())
        for (const mode of MODES) {
            expect(MONO_BASE_PX[mode]).toBeDefined()
            expect(typeof MONO_BASE_PX[mode]).toBe("number")
        }
    })

    test("values increase monotonically from small through large in both maps", () => {
        expect(BASE_PX.small).toBeLessThan(BASE_PX.medium)
        expect(BASE_PX.medium).toBeLessThan(BASE_PX.large)

        expect(MONO_BASE_PX.small).toBeLessThan(MONO_BASE_PX.medium)
        expect(MONO_BASE_PX.medium).toBeLessThan(MONO_BASE_PX.large)
    })

    test("monospace sizes are no larger than their proportional counterparts for the same mode", () => {
        for (const mode of MODES) {
            expect(MONO_BASE_PX[mode]).toBeLessThanOrEqual(BASE_PX[mode])
        }
    })
})
