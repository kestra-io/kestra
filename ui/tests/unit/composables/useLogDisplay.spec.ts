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
        const {logsFontSize, appFontSizeMode} = await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(logsFontSize.value).toBe(12)

        appFontSizeMode.value = "small"
        expect(logsFontSize.value).toBe(11)

        appFontSizeMode.value = "large"
        expect(logsFontSize.value).toBe(14)
    })

    it("effective editor font size defaults to mono base px (same as logs)", async () => {
        const {effectiveEditorFontSize, appFontSizeMode} = await import("../../../src/composables/useLogDisplay")

        appFontSizeMode.value = "medium"
        expect(effectiveEditorFontSize.value).toBe(12)

        appFontSizeMode.value = "small"
        expect(effectiveEditorFontSize.value).toBe(11)

        appFontSizeMode.value = "large"
        expect(effectiveEditorFontSize.value).toBe(14)
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
            await import("../../../src/composables/useLogDisplay")
        appFontSizeMode.value = "large"
        logsFontSizeOverride.value = 11
        expect(logsFontSize.value).toBe(11)
        logsFontSizeOverride.value = null
        expect(logsFontSize.value).toBe(14)
    })

    it.each([
        ["logsDensity", "compact", "expanded"],
        ["logsBodyClamp", "7", "12"],
        ["logsPrettyJson", "false", "true"],
        ["logsExpandByDefault", "true", "false"],
    ])("%s reads its stored value on first access and persists changes", async (key, storedValue, nextValue) => {
        localStorage.setItem(key, storedValue)
        const preferences = await import("../../../src/composables/useLogDisplay")
        const refs = {
            logsDensity: preferences.logsDensity,
            logsBodyClamp: preferences.logsBodyClamp,
            logsPrettyJson: preferences.logsPrettyJson,
            logsExpandByDefault: preferences.logsExpandByDefault,
        }
        const ref = refs[key as keyof typeof refs]
        const expectedStored = key === "logsBodyClamp" ? Number(storedValue) : key === "logsPrettyJson" || key === "logsExpandByDefault" ? storedValue === "true" : storedValue
        const expectedNext = key === "logsBodyClamp" ? Number(nextValue) : key === "logsPrettyJson" || key === "logsExpandByDefault" ? nextValue === "true" : nextValue
        expect(ref.value).toBe(expectedStored)
        ref.value = expectedNext as never
        await Promise.resolve()
        expect(localStorage.getItem(key)).toBe(nextValue)
    })

    it.each([
        ["logsDensity", "invalid", "normal"],
        ["logsBodyClamp", "not-a-number", 0],
        ["logsPrettyJson", "not-a-boolean", true],
        ["logsExpandByDefault", "not-a-boolean", false],
    ])("%s falls back to its default for missing and malformed values", async (key, malformedValue, defaultValue) => {
        const preferences = await import("../../../src/composables/useLogDisplay")
        const refs = {
            logsDensity: preferences.logsDensity,
            logsBodyClamp: preferences.logsBodyClamp,
            logsPrettyJson: preferences.logsPrettyJson,
            logsExpandByDefault: preferences.logsExpandByDefault,
        }
        expect(refs[key as keyof typeof refs].value).toBe(defaultValue)
        localStorage.setItem(key, String(malformedValue))
        vi.resetModules()
        const reloaded = await import("../../../src/composables/useLogDisplay")
        const reloadedRefs = {
            logsDensity: reloaded.logsDensity,
            logsBodyClamp: reloaded.logsBodyClamp,
            logsPrettyJson: reloaded.logsPrettyJson,
            logsExpandByDefault: reloaded.logsExpandByDefault,
        }
        expect(reloadedRefs[key as keyof typeof reloadedRefs].value).toBe(defaultValue)
    })

    it("keeps the same preference in sync between two callers", async () => {
        const first = await import("../../../src/composables/useLogDisplay")
        const second = await import("../../../src/composables/useLogDisplay")
        first.logsDensity.value = "compact"
        expect(second.logsDensity.value).toBe("compact")
        expect(localStorage.getItem("logsDensity")).toBe("compact")
    })

    it("has density padding for every density offered by the picker", async () => {
        const {DENSITY_PADDING} = await import("../../../src/composables/useLogDisplay")
        expect(Object.keys(DENSITY_PADDING).sort()).toEqual(["compact", "expanded", "normal"])
        expect(DENSITY_PADDING.compact).toBe("2px")
        expect(DENSITY_PADDING.normal).toBe("5px")
        expect(DENSITY_PADDING.expanded).toBe("12px")
    })
})
