import {nextTick} from "vue"
import type {LogDensity} from "./useLogDisplay"
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

    it("malformed font size overrides fall back to the mono base px instead of throwing", async () => {
        localStorage.setItem("logsFontSize", "not-a-number")
        localStorage.setItem("editorFontSize", "invalid")

        const {logsFontSize, effectiveEditorFontSize} = await import("./useLogDisplay")

        expect(logsFontSize.value).toBe(12)
        expect(effectiveEditorFontSize.value).toBe(12)
    })

    // useStorage does not validate these four: it passes the raw value through its type serializer
    it("malformed stored values do not throw and are read as the serializer parses them", async () => {
        localStorage.setItem("logsDensity", "bogus")
        localStorage.setItem("logsBodyClamp", "not-a-number")
        localStorage.setItem("logsPrettyJson", "not-a-boolean")
        localStorage.setItem("logsExpandByDefault", "not-a-boolean")

        const {logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault} =
            await import("./useLogDisplay")

        expect(logsDensity.value).toBe("bogus")
        expect(Number.isNaN(logsBodyClamp.value)).toBe(true)
        expect(logsPrettyJson.value).toBe(false)
        expect(logsExpandByDefault.value).toBe(false)
    })

    it("same preference shared between two callers stays in sync", async () => {
        const {logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault} =
            await import("./useLogDisplay")
        const {useStorage} = await import("@vueuse/core")

        const otherDensity = useStorage<LogDensity>("logsDensity", "normal")
        const otherBodyClamp = useStorage<number>("logsBodyClamp", 0)
        const otherPrettyJson = useStorage<boolean>("logsPrettyJson", true)
        const otherExpandByDefault = useStorage<boolean>("logsExpandByDefault", false)

        logsDensity.value = "compact"
        logsBodyClamp.value = 15
        logsPrettyJson.value = false
        logsExpandByDefault.value = true
        await nextTick()

        expect(otherDensity.value).toBe("compact")
        expect(otherBodyClamp.value).toBe(15)
        expect(otherPrettyJson.value).toBe(false)
        expect(otherExpandByDefault.value).toBe(true)

        otherDensity.value = "expanded"
        await nextTick()
        expect(logsDensity.value).toBe("expanded")
    })

    it("DENSITY_PADDING has an entry for every density the picker offers", async () => {
        const {DENSITY_PADDING} = await import("./useLogDisplay")
        // Record<LogDensity, true> fails the type check when a density is added to the type but not listed here
        const densities: Record<LogDensity, true> = {compact: true, normal: true, expanded: true}

        expect(Object.keys(DENSITY_PADDING).sort()).toEqual(Object.keys(densities).sort())
        for (const density of Object.keys(densities) as LogDensity[]) {
            expect(DENSITY_PADDING[density]).toMatch(/^\d+px$/)
        }
    })
})
