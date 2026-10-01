import {describe, it, expect} from "vitest"

import {buildShortcutGroups} from "../../../../../src/components/no-code/blocks/shortcutHints"

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
