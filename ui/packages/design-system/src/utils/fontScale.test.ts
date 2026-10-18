import {describe, expect, it} from "vitest"
import {
    APP_FONT_SIZE_KEY,
    BASE_PX,
    MONO_BASE_PX,
    type AppFontSizeMode,
} from "./fontScale"

const FONT_MODES: AppFontSizeMode[] = ["small", "medium", "large"]

describe("fontScale", () => {
    describe("BASE_PX", () => {
        it("has an entry for every AppFontSizeMode", () => {
            const keys = Object.keys(BASE_PX)
            expect(keys).toHaveLength(FONT_MODES.length)
            for (const mode of FONT_MODES) {
                expect(BASE_PX[mode]).toBeDefined()
                expect(typeof BASE_PX[mode]).toBe("number")
                expect(BASE_PX[mode]).toBeGreaterThan(0)
            }
        })

        it("values strictly increase from small through large", () => {
            expect(BASE_PX.small).toBeLessThan(BASE_PX.medium)
            expect(BASE_PX.medium).toBeLessThan(BASE_PX.large)
            expect(BASE_PX).toEqual({
                small: 12,
                medium: 14,
                large: 16,
            })
        })
    })

    describe("MONO_BASE_PX", () => {
        it("has the same set of keys as BASE_PX and AppFontSizeMode", () => {
            const monoKeys = Object.keys(MONO_BASE_PX).sort()
            const baseKeys = Object.keys(BASE_PX).sort()
            expect(monoKeys).toEqual(baseKeys)

            for (const mode of FONT_MODES) {
                expect(MONO_BASE_PX[mode]).toBeDefined()
                expect(typeof MONO_BASE_PX[mode]).toBe("number")
                expect(MONO_BASE_PX[mode]).toBeGreaterThan(0)
            }
        })

        it("values strictly increase from small through large", () => {
            expect(MONO_BASE_PX.small).toBeLessThan(MONO_BASE_PX.medium)
            expect(MONO_BASE_PX.medium).toBeLessThan(MONO_BASE_PX.large)
            expect(MONO_BASE_PX).toEqual({
                small: 11,
                medium: 12,
                large: 14,
            })
        })

        it("mono sizes are no larger than their proportional counterparts", () => {
            for (const mode of FONT_MODES) {
                expect(MONO_BASE_PX[mode]).toBeLessThanOrEqual(BASE_PX[mode])
            }
        })
    })

    describe("APP_FONT_SIZE_KEY", () => {
        it("is the expected storage key the app reads", () => {
            expect(APP_FONT_SIZE_KEY).toBe("appFontSize")
            expect(typeof APP_FONT_SIZE_KEY).toBe("string")
        })
    })
})
