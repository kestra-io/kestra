import {describe, it, expect} from "vitest"

import {buildModifierDisplay, buildShortcutGroups, computeIsMac} from "../../../../../src/components/no-code/blocks/shortcutHints"

function allIds(groups: ReturnType<typeof buildShortcutGroups>): string[] {
    return groups.flatMap(group => group.bindings.map(binding => binding.id))
}

describe("buildShortcutGroups", () => {
    it("lists copy/cut/paste by default, for the no-code block editor", () => {
        const ids = allIds(buildShortcutGroups())

        expect(ids).toContain("copy")
        expect(ids).toContain("cut")
        expect(ids).toContain("paste")
    })

    it("omits copy/cut/paste when the surface does not support clipboard actions, e.g. the topology canvas", () => {
        const ids = allIds(buildShortcutGroups({supportsClipboard: false}))

        expect(ids).not.toContain("copy")
        expect(ids).not.toContain("cut")
        expect(ids).not.toContain("paste")
        // The rest of the keymap is untouched
        expect(ids).toContain("duplicate")
        expect(ids).toContain("redo")
    })
})

describe("computeIsMac", () => {
    it("detects a Mac from the platform string", () => {
        expect(computeIsMac({platform: "MacIntel", userAgent: "Mozilla/5.0 (Macintosh)"})).toBe(true)
    })

    it("does not detect a Mac on Windows or Linux", () => {
        expect(computeIsMac({platform: "Win32", userAgent: "Mozilla/5.0 (Windows NT 10.0)"})).toBe(false)
        expect(computeIsMac({platform: "Linux x86_64", userAgent: "Mozilla/5.0 (X11; Linux x86_64)"})).toBe(false)
    })
})

describe("buildModifierDisplay", () => {
    it("renders modifiers as Mac glyphs when isMac is true", () => {
        const display = buildModifierDisplay(true)

        expect(display.Meta).toBe("⌘")
        expect(display.Control).toBe("⌘")
        expect(display.Shift).toBe("⇧")
        expect(display.Alt).toBe("⌥")
    })

    it("renders modifiers as Windows/Linux labels when isMac is false", () => {
        // Regression: every Control+ binding used to render as a Mac glyph whatever the platform.
        const display = buildModifierDisplay(false)

        expect(display.Meta).toBe("Ctrl+")
        expect(display.Control).toBe("Ctrl+")
        expect(display.Shift).toBe("Shift+")
        expect(display.Alt).toBe("Alt+")
    })
})
