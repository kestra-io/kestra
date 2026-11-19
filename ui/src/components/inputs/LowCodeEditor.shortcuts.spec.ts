import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"
import LowCodeEditor from "./LowCodeEditor.vue"

/** The shortcut ids the topology's `?` dialog lists. */
function listedShortcuts(): string[] {
    const wrapper = i18nShallowMount(LowCodeEditor, {
        props: {flowGraph: {nodes: [], edges: [], clusters: []}, isReadOnly: false},
        global: {plugins: [createPinia()]},
    })
    const groups: {bindings: {id: string}[]}[] = wrapper.findComponent({name: "BlockShortcutsDialog"}).props("groups")
    return groups.flatMap(group => group.bindings.map(binding => binding.id))
}

describe("LowCodeEditor shortcuts", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("lists undo and redo but not the clipboard shortcuts the topology canvas cannot handle", () => {
        const ids = listedShortcuts()

        expect(ids).toEqual(expect.arrayContaining(["undo", "redo"]))
        expect(ids).not.toContain("copy")
        expect(ids).not.toContain("cut")
        expect(ids).not.toContain("paste")
    })
})
