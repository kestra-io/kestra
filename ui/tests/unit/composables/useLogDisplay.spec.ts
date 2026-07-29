import {describe, it, expect, beforeEach, vi} from "vitest"

describe("useLogDisplay", () => {
    beforeEach(() => {
        localStorage.clear()
        vi.resetModules()
    })

    it("effective logs font size defaults to mode base px", async () => {
        const {logsFontSize, appFontSizeMode} = await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(logsFontSize.value).toBe(14)

        appFontSizeMode.value = "small"
        expect(logsFontSize.value).toBe(12)

        appFontSizeMode.value = "large"
        expect(logsFontSize.value).toBe(16)
    })

    it("effective editor font size defaults to mode base px (same as logs)", async () => {
        const {effectiveEditorFontSize, appFontSizeMode} = await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(effectiveEditorFontSize.value).toBe(14)

        appFontSizeMode.value = "small"
        expect(effectiveEditorFontSize.value).toBe(12)

        appFontSizeMode.value = "large"
        expect(effectiveEditorFontSize.value).toBe(16)
    })

    it("explicit override is preserved and not snapped back on mode switch", async () => {
        const {logsFontSizeOverride, editorFontSizeOverride, logsFontSize, effectiveEditorFontSize, appFontSizeMode} =
            await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "medium"

        logsFontSizeOverride.value = 11
        expect(logsFontSize.value).toBe(11)

        editorFontSizeOverride.value = 20
        expect(effectiveEditorFontSize.value).toBe(20)

        appFontSizeMode.value = "large"
        expect(logsFontSize.value).toBe(11)
        expect(effectiveEditorFontSize.value).toBe(20)
    })

    it("explicit override of 14 for logs is kept when migration already ran", async () => {
        localStorage.setItem("_fontMigratedV2", "1")
        localStorage.setItem("logsFontSize", "14")

        const {logsFontSize, appFontSizeMode} = await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "small"
        expect(logsFontSize.value).toBe(14)
    })

    it("explicit override of 12 for editor is kept when migration already ran", async () => {
        localStorage.setItem("_fontMigratedV2", "1")
        localStorage.setItem("editorFontSize", "12")

        const {effectiveEditorFontSize, appFontSizeMode} = await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "large"
        expect(effectiveEditorFontSize.value).toBe(12)
    })

    it("one-time migration clears legacy logs default 14", async () => {
        localStorage.setItem("logsFontSize", "14")

        await import("../../../src/composables/useLogDisplay")

        expect(localStorage.getItem("logsFontSize")).toBeNull()
        expect(localStorage.getItem("_fontMigratedV2")).toBe("1")
    })

    it("one-time migration clears legacy editor default 12", async () => {
        localStorage.setItem("editorFontSize", "12")

        await import("../../../src/composables/useLogDisplay")

        expect(localStorage.getItem("editorFontSize")).toBeNull()
    })

    it("one-time migration does not clear non-legacy override values", async () => {
        localStorage.setItem("logsFontSize", "11")
        localStorage.setItem("editorFontSize", "18")

        await import("../../../src/composables/useLogDisplay")

        expect(localStorage.getItem("logsFontSize")).toBe("11")
        expect(localStorage.getItem("editorFontSize")).toBe("18")
    })

    it("migration runs only once: legacy value set after flag is treated as explicit override", async () => {
        localStorage.setItem("_fontMigratedV2", "1")
        localStorage.setItem("logsFontSize", "14")

        await import("../../../src/composables/useLogDisplay")

        expect(localStorage.getItem("logsFontSize")).toBe("14")
    })

    it("effective values react to mode change", async () => {
        const {logsFontSize, effectiveEditorFontSize, appFontSizeMode} =
            await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(logsFontSize.value).toBe(14)
        expect(effectiveEditorFontSize.value).toBe(14)

        appFontSizeMode.value = "large"
        expect(logsFontSize.value).toBe(16)
        expect(effectiveEditorFontSize.value).toBe(16)

        appFontSizeMode.value = "small"
        expect(logsFontSize.value).toBe(12)
        expect(effectiveEditorFontSize.value).toBe(12)
    })

    it("clearing override restores mode-derived default", async () => {
        const {logsFontSizeOverride, logsFontSize, appFontSizeMode} =
            await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "large"
        logsFontSizeOverride.value = 11
        expect(logsFontSize.value).toBe(11)

        logsFontSizeOverride.value = null
        expect(logsFontSize.value).toBe(16)
    })
})
