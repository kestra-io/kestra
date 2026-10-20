import {afterEach, describe, it, expect, vi} from "vitest"
import type {BlockCommandMenuContext} from "./blockCommandMenu"

function makeContext(): BlockCommandMenuContext {
    const noop = vi.fn()
    return {
        t: (key: string) => key,
        focusedId: "log",
        focusedBlockDisplayName: () => "log",
        sectionDisplayLabel: (section: string) => section,
        laneDisplayLabelFromPath: (path: string) => path,
        close: noop,
        addAfterFocused: noop,
        addBeforeFocused: noop,
        insertInSection: noop,
        openFocused: noop,
        duplicateFocused: noop,
        deleteFocused: noop,
        goToSection: noop,
        saveFlow: noop,
        taskEntries: [],
        insertTaskType: noop,
        copyFocused: noop,
        cutFocused: noop,
        pasteRelative: noop,
        canPaste: true,
    } as unknown as BlockCommandMenuContext
}

async function shortcutsFor(platform: string) {
    vi.resetModules()
    vi.stubGlobal("navigator", {platform, userAgent: platform})
    const {buildCommandMenuItems} = await import("./blockCommandMenu")
    const items = buildCommandMenuItems(makeContext())
    return Object.fromEntries(items.map(item => [item.id, item.shortcut]))
}

describe("buildCommandMenuItems shortcuts", () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it("renders modifier shortcuts with Ctrl and Shift words on non-Mac platforms", async () => {
        // Given / When
        const shortcuts = await shortcutsFor("Win32")

        // Then
        expect(shortcuts).toMatchObject({
            copy: "Ctrl+C",
            cut: "Ctrl+X",
            paste: "Ctrl+V",
            save: "Ctrl+S",
            "insert-before": "Shift+A",
        })
    })

    it("renders modifier shortcuts with Mac glyphs on Mac", async () => {
        // Given / When
        const shortcuts = await shortcutsFor("MacIntel")

        // Then
        expect(shortcuts).toMatchObject({
            copy: "⌘C",
            cut: "⌘X",
            paste: "⌘V",
            save: "⌘S",
            "insert-before": "⇧A",
        })
    })
})
