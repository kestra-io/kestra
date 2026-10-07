import {nextTick} from "vue"
import {describe, it, expect, afterAll, beforeEach, vi} from "vitest"

describe("useLogDisplay", () => {
    beforeEach(() => {
        localStorage.clear()
        vi.resetModules()
    })

    afterAll(() => {
        localStorage.clear()
    })

    it("effective logs font size defaults to mono base px", async () => {
        const {logsFontSize, appFontSizeMode} = await import("./useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(logsFontSize.value).toBe(12)

        appFontSizeMode.value = "small"
        expect(logsFontSize.value).toBe(11)

        appFontSizeMode.value = "large"
        expect(logsFontSize.value).toBe(14)
    })

    it("effective editor font size defaults to mono base px (same as logs)", async () => {
        const {effectiveEditorFontSize, appFontSizeMode} = await import("./useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(effectiveEditorFontSize.value).toBe(12)

        appFontSizeMode.value = "small"
        expect(effectiveEditorFontSize.value).toBe(11)

        appFontSizeMode.value = "large"
        expect(effectiveEditorFontSize.value).toBe(14)
    })

    it("explicit override is preserved and not snapped back on mode switch", async () => {
        const {logsFontSizeOverride, editorFontSizeOverride, logsFontSize, effectiveEditorFontSize, appFontSizeMode} =
            await import("./useLogDisplay")

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

        const {logsFontSize, appFontSizeMode} = await import("./useLogDisplay")

        appFontSizeMode.value = "small"
        expect(logsFontSize.value).toBe(14)
    })

    it("explicit override of 12 for editor is kept when migration already ran", async () => {
        localStorage.setItem("_fontMigratedV2", "1")
        localStorage.setItem("editorFontSize", "12")

        const {effectiveEditorFontSize, appFontSizeMode} = await import("./useLogDisplay")

        appFontSizeMode.value = "large"
        expect(effectiveEditorFontSize.value).toBe(12)
    })

    it("one-time migration clears legacy logs default 14", async () => {
        localStorage.setItem("logsFontSize", "14")

        await import("./useLogDisplay")

        expect(localStorage.getItem("logsFontSize")).toBeNull()
        expect(localStorage.getItem("_fontMigratedV2")).toBe("1")
    })

    it("one-time migration clears legacy editor default 12", async () => {
        localStorage.setItem("editorFontSize", "12")

        await import("./useLogDisplay")

        expect(localStorage.getItem("editorFontSize")).toBeNull()
    })

    it("one-time migration does not clear non-legacy override values", async () => {
        localStorage.setItem("logsFontSize", "11")
        localStorage.setItem("editorFontSize", "18")

        await import("./useLogDisplay")

        expect(localStorage.getItem("logsFontSize")).toBe("11")
        expect(localStorage.getItem("editorFontSize")).toBe("18")
    })

    it("migration runs only once: legacy value set after flag is treated as explicit override", async () => {
        localStorage.setItem("_fontMigratedV2", "1")
        localStorage.setItem("logsFontSize", "14")

        await import("./useLogDisplay")

        expect(localStorage.getItem("logsFontSize")).toBe("14")
    })

    it("effective values react to mode change", async () => {
        const {logsFontSize, effectiveEditorFontSize, appFontSizeMode} =
            await import("./useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(logsFontSize.value).toBe(12)
        expect(effectiveEditorFontSize.value).toBe(12)

        appFontSizeMode.value = "large"
        expect(logsFontSize.value).toBe(14)
        expect(effectiveEditorFontSize.value).toBe(14)

        appFontSizeMode.value = "small"
        expect(logsFontSize.value).toBe(11)
        expect(effectiveEditorFontSize.value).toBe(11)
    })

    it("clearing override restores mode-derived default", async () => {
        const {logsFontSizeOverride, logsFontSize, appFontSizeMode} =
            await import("./useLogDisplay")

        appFontSizeMode.value = "large"
        logsFontSizeOverride.value = 11
        expect(logsFontSize.value).toBe(11)

        logsFontSizeOverride.value = null
        expect(logsFontSize.value).toBe(14)
    })

    it("reads stored preference values on first access", async () => {
        localStorage.setItem("logsDensity", "compact")
        localStorage.setItem("logsBodyClamp", "10")
        localStorage.setItem("logsPrettyJson", "false")
        localStorage.setItem("logsExpandByDefault", "true")

        const {logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault} =
            await import("./useLogDisplay")

        expect(logsDensity.value).toBe("compact")
        expect(logsBodyClamp.value).toBe(10)
        expect(logsPrettyJson.value).toBe(false)
        expect(logsExpandByDefault.value).toBe(true)
    })

    it("writes back preference values when changed", async () => {
        const {logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault} =
            await import("./useLogDisplay")

        logsDensity.value = "expanded"
        await nextTick()
        expect(localStorage.getItem("logsDensity")).toBe("expanded")

        logsBodyClamp.value = 5
        await nextTick()
        expect(localStorage.getItem("logsBodyClamp")).toBe("5")

        logsPrettyJson.value = false
        await nextTick()
        expect(localStorage.getItem("logsPrettyJson")).toBe("false")

        logsExpandByDefault.value = true
        await nextTick()
        expect(localStorage.getItem("logsExpandByDefault")).toBe("true")
    })

    it("missing stored values fall back to documented defaults", async () => {
        const {logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault} =
            await import("./useLogDisplay")

        expect(logsDensity.value).toBe("normal")
        expect(logsBodyClamp.value).toBe(0)
        expect(logsPrettyJson.value).toBe(true)
        expect(logsExpandByDefault.value).toBe(false)
    })

    it("malformed stored value falls back to the default instead of throwing", async () => {
        localStorage.setItem("logsFontSize", "not-a-number")
        localStorage.setItem("editorFontSize", "invalid")

        const {logsFontSize, effectiveEditorFontSize, logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault} =
            await import("./useLogDisplay")

        expect(() => logsFontSize.value).not.toThrow()
        expect(logsFontSize.value).toBe(12)
        expect(() => effectiveEditorFontSize.value).not.toThrow()
        expect(effectiveEditorFontSize.value).toBe(12)

        expect(() => logsDensity.value).not.toThrow()
        expect(() => logsBodyClamp.value).not.toThrow()
        expect(() => logsPrettyJson.value).not.toThrow()
        expect(() => logsExpandByDefault.value).not.toThrow()
    })

    it("same preference shared between two callers stays in sync", async () => {
        const first = await import("./useLogDisplay")
        const second = await import("./useLogDisplay")

        first.logsDensity.value = "compact"
        expect(second.logsDensity.value).toBe("compact")

        second.logsPrettyJson.value = false
        expect(first.logsPrettyJson.value).toBe(false)

        first.logsBodyClamp.value = 15
        expect(second.logsBodyClamp.value).toBe(15)

        second.logsExpandByDefault.value = true
        expect(first.logsExpandByDefault.value).toBe(true)
    })

    it("DENSITY_PADDING has an entry for every density the picker offers", async () => {
        const {DENSITY_PADDING} = await import("./useLogDisplay")
        const pickerDensities = ["compact", "normal", "expanded"] as const

        for (const density of pickerDensities) {
            expect(DENSITY_PADDING[density]).toBeDefined()
            expect(typeof DENSITY_PADDING[density]).toBe("string")
            expect(DENSITY_PADDING[density].length).toBeGreaterThan(0)
        }
    })
})
