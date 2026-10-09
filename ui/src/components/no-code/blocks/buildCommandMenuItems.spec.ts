import {describe, it, expect, vi} from "vitest"
import {buildCommandMenuItems, type BlockCommandMenuContext} from "./blockCommandMenu"

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

describe("buildCommandMenuItems shortcuts", () => {
    it("renders modifier shortcuts with the platform glyph, like the footer and help", () => {
        // Given
        const items = buildCommandMenuItems(makeContext())

        // When
        const shortcuts = Object.fromEntries(items.map(item => [item.id, item.shortcut]))

        // Then
        expect(shortcuts).toMatchObject({
            copy: "Ctrl+C",
            cut: "Ctrl+X",
            paste: "Ctrl+V",
            save: "Ctrl+S",
            "insert-before": "Shift+A",
        })
    })
})
