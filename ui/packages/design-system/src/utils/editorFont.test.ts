import {afterEach, describe, expect, test} from "vitest"
import {DEFAULT_EDITOR_FONT_FAMILY, EDITOR_FONT_FAMILY_STORAGE_KEY, editorFontFamily} from "./editorFont"

describe("editorFontFamily", () => {
    afterEach(() => localStorage.removeItem(EDITOR_FONT_FAMILY_STORAGE_KEY))

    test("defaults to the bundled JetBrains Mono when nothing is stored", () => {
        expect(editorFontFamily()).toBe(DEFAULT_EDITOR_FONT_FAMILY)
    })

    test("uses the font chosen in the preferences", () => {
        localStorage.setItem(EDITOR_FONT_FAMILY_STORAGE_KEY, "'Fira Code', monospace")
        expect(editorFontFamily()).toBe("'Fira Code', monospace")
    })
})
