import {describe, it, expect} from "vitest"

import {BLOCK_EDITOR_KEYMAP, blockEditorKeymapByGroup, findBlockEditorBinding} from "../../../../../src/components/no-code/blocks/keymap"

describe("keymap", () => {
    it("gives every binding a unique id", () => {
        // Given

        // When
        const ids = BLOCK_EDITOR_KEYMAP.map(binding => binding.id)

        // Then
        expect(new Set(ids).size).toBe(ids.length)
    })

    it("gives every binding at least one key and a resolvable i18n key", () => {
        // Given

        // When / Then
        for (const binding of BLOCK_EDITOR_KEYMAP) {
            expect(binding.keys.length).toBeGreaterThan(0)
            expect(binding.i18nKey.startsWith("block_editor.")).toBe(true)
        }
    })

    it("covers the navigate, insert, edit and global groups", () => {
        // Given
        const expectedGroups = ["navigate", "insert", "edit", "global"]

        // When
        const groups = new Set(BLOCK_EDITOR_KEYMAP.map(binding => binding.group))

        // Then
        for (const group of expectedGroups) {
            expect(groups.has(group as never)).toBe(true)
        }
    })

    it("finds a binding by id", () => {
        // Given

        // When
        const binding = findBlockEditorBinding("delete")

        // Then
        expect(binding?.keys).toContain("Backspace")
    })

    it("returns undefined for an unknown id", () => {
        // Given

        // When
        const binding = findBlockEditorBinding("does-not-exist")

        // Then
        expect(binding).toBeUndefined()
    })

    it("filters bindings by group", () => {
        // Given

        // When
        const navigateBindings = blockEditorKeymapByGroup("navigate")

        // Then
        expect(navigateBindings.length).toBeGreaterThan(0)
        expect(navigateBindings.every(binding => binding.group === "navigate")).toBe(true)
    })

    it("keeps the documented keymap: navigate, insert, edit and global shortcuts", () => {
        // Given
        const byId = new Map(BLOCK_EDITOR_KEYMAP.map(binding => [binding.id, binding]))

        // Then — the exact keymap this feature was designed against
        expect(byId.get("move")?.keys).toEqual(["ArrowUp", "ArrowDown"])
        expect(byId.get("step-into")?.keys).toEqual(["ArrowRight"])
        expect(byId.get("step-out")?.keys).toEqual(["ArrowLeft"])
        expect(byId.get("open")?.keys).toEqual(["Enter"])
        expect(byId.get("open-split")?.keys).toEqual(["Meta+Enter", "Control+Enter"])
        expect(byId.get("clear")?.keys).toEqual(["Escape"])
        expect(byId.get("insert-after")?.keys).toEqual(["a"])
        expect(byId.get("insert-before")?.keys).toEqual(["Shift+a"])
        expect(byId.get("quick-insert")?.keys).toEqual(["/"])
        // Not Meta+k: that's already the app-wide "Jump to" global search (GlobalSearch.vue).
        // Not Meta+Shift+k either: it collides with shortcuts in common companion apps
        // (e.g. Notion) — Meta+Shift+p matches the conventional "command palette" binding.
        expect(byId.get("command-menu")?.keys).toEqual(["Meta+Shift+p", "Control+Shift+p"])
        expect(byId.get("duplicate")?.keys).toEqual(["d"])
        expect(byId.get("delete")?.keys).toEqual(["Backspace", "Delete"])
        expect(byId.get("reorder")?.keys).toEqual(["Alt+ArrowUp", "Alt+ArrowDown"])
        expect(byId.get("save")?.keys).toEqual(["Meta+s", "Control+s"])
        expect(byId.get("undo")?.keys).toEqual(["Meta+z", "Control+z"])
        expect(byId.get("help")?.keys).toEqual(["?"])
    })
})
