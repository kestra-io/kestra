import {describe, expect, test} from "vitest"
import BlockShortcutsDialog from "./BlockShortcutsDialog.vue"
import type {BlockEditorKeyBinding, BlockEditorKeymapGroup} from "./keymap"
import {i18nMount} from "../../../../tests/unit/i18nMount"

type ShortcutGroup = {group: BlockEditorKeymapGroup; bindings: BlockEditorKeyBinding[]}

const KsDialog = {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<div><slot /></div>",
}

function binding(id: string, keys: string[], alt?: string[]): BlockEditorKeyBinding {
    return {id, keys, alt, group: "navigate", i18nKey: `shortcut.${id}`}
}

function mountDialog({open = true, groups = [] as ShortcutGroup[]} = {}) {
    return i18nMount(BlockShortcutsDialog, {
        props: {open, groups},
        global: {stubs: {KsDialog}},
    })
}

describe("BlockShortcutsDialog", () => {
    test("renders one column per group, each with its heading", () => {
        const wrapper = mountDialog({
            groups: [
                {group: "navigate", bindings: []},
                {group: "edit", bindings: []},
            ],
        })

        const columns = wrapper.findAll(".block-editor-shortcuts-col")
        expect(columns).toHaveLength(2)
        expect(columns[0].find(".block-editor-shortcuts-heading").text()).toBe("block_editor.shortcuts.group_navigate")
        expect(columns[1].find(".block-editor-shortcuts-heading").text()).toBe("block_editor.shortcuts.group_edit")
    })

    test("renders one row per binding, with every key its own kbd", () => {
        const wrapper = mountDialog({
            groups: [{group: "navigate", bindings: [binding("first", ["a", "b"]), binding("second", ["c"])]}],
        })

        const rows = wrapper.findAll(".block-editor-shortcut")
        expect(rows).toHaveLength(2)
        expect(rows[0].findAll("kbd").map(kbd => kbd.text())).toEqual(["a", "b"])
        expect(rows[1].findAll("kbd").map(kbd => kbd.text())).toEqual(["c"])
        expect(rows[0].text()).toContain("shortcut.first")
    })

    test("shows the separator and the alternative keys only when alt is non-empty", () => {
        const wrapper = mountDialog({
            groups: [{group: "navigate", bindings: [binding("with-alt", ["a"], ["x", "y"]), binding("without-alt", ["b"])]}],
        })

        const [withAlt, withoutAlt] = wrapper.findAll(".block-editor-shortcut")
        expect(withAlt.find(".block-editor-shortcut-or").exists()).toBe(true)
        expect(withAlt.findAll("kbd").map(kbd => kbd.text())).toEqual(["a", "x", "y"])
        expect(withoutAlt.find(".block-editor-shortcut-or").exists()).toBe(false)
        expect(withoutAlt.findAll("kbd").map(kbd => kbd.text())).toEqual(["b"])
    })

    test("renders no separator for a binding with an empty alt array", () => {
        const wrapper = mountDialog({
            groups: [{group: "navigate", bindings: [binding("empty-alt", ["a"], [])]}],
        })

        const row = wrapper.find(".block-editor-shortcut")
        expect(row.find(".block-editor-shortcut-or").exists()).toBe(false)
        expect(row.findAll("kbd")).toHaveLength(1)
    })

    test("passes the open prop to the dialog", async () => {
        const wrapper = mountDialog({open: false})
        expect(wrapper.findComponent(KsDialog).props("modelValue")).toBe(false)

        await wrapper.setProps({open: true})
        expect(wrapper.findComponent(KsDialog).props("modelValue")).toBe(true)
    })

    test("emits update:open with false when the dialog closes", () => {
        const wrapper = mountDialog()

        wrapper.findComponent(KsDialog).vm.$emit("update:modelValue", false)

        expect(wrapper.emitted("update:open")).toEqual([[false]])
    })

    test("coerces an undefined close payload to false", () => {
        const wrapper = mountDialog()

        wrapper.findComponent(KsDialog).vm.$emit("update:modelValue", undefined)

        expect(wrapper.emitted("update:open")).toEqual([[false]])
    })
})