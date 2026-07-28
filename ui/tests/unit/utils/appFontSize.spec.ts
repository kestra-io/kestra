import {describe, it, expect, beforeEach, afterEach} from "vitest"
import {appFontSizeInfo, getAppFontSizeMode, applyFontScale, APP_FONT_SIZE_KEY, type AppFontSizeMode} from "../../../src/utils/appFontSize"

describe("appFontSizeInfo", () => {
    it("returns scale 1 and base 14 for medium", () => {
        const {scale, base} = appFontSizeInfo("medium")
        expect(scale).toBe(1)
        expect(base).toBe(14)
    })

    it("returns scale 12/14 and base 12 for small", () => {
        const {scale, base} = appFontSizeInfo("small")
        expect(scale).toBeCloseTo(12 / 14)
        expect(base).toBe(12)
    })

    it("returns scale 16/14 and base 16 for large", () => {
        const {scale, base} = appFontSizeInfo("large")
        expect(scale).toBeCloseTo(16 / 14)
        expect(base).toBe(16)
    })
})

describe("getAppFontSizeMode", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("returns medium when no value stored", () => {
        expect(getAppFontSizeMode()).toBe("medium")
    })

    it("returns stored valid mode", () => {
        localStorage.setItem(APP_FONT_SIZE_KEY, "large")
        expect(getAppFontSizeMode()).toBe("large")
    })

    it("returns medium for invalid stored value", () => {
        localStorage.setItem(APP_FONT_SIZE_KEY, "huge")
        expect(getAppFontSizeMode()).toBe("medium")
    })
})

describe("applyFontScale", () => {
    const modes: AppFontSizeMode[] = ["small", "medium", "large"]

    beforeEach(() => {
        localStorage.clear()
        document.documentElement.style.removeProperty("--ks-font-scale")
    })

    afterEach(() => {
        localStorage.clear()
    })

    it.each(modes)("sets --ks-font-scale on html element for %s mode", (mode) => {
        applyFontScale(mode)
        const value = document.documentElement.style.getPropertyValue("--ks-font-scale")
        const {scale} = appFontSizeInfo(mode)
        expect(parseFloat(value)).toBeCloseTo(scale)
    })
})
