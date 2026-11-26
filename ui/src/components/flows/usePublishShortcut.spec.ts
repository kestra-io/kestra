import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {computed, defineComponent, h, ref} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"

const publish = vi.fn()
const isReadOnly = ref(false)
const hasErrors = ref(false)

vi.mock("./useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({
        publish,
        isReadOnly: computed(() => isReadOnly.value),
        hasErrors: computed(() => hasErrors.value),
    }),
}))

import {usePublishShortcut} from "./usePublishShortcut"

function press(init: KeyboardEventInit) {
    window.dispatchEvent(new KeyboardEvent("keydown", {bubbles: true, code: "KeyS", ...init}))
}

describe("usePublishShortcut", () => {
    let wrapper: VueWrapper

    beforeEach(() => {
        publish.mockClear()
        isReadOnly.value = false
        hasErrors.value = false
        wrapper = mount(defineComponent({setup: () => usePublishShortcut(), render: () => h("div")}))
    })

    afterEach(() => wrapper.unmount())

    it.each([{ctrlKey: true, key: "s"}, {metaKey: true, key: "ß"}])("publishes on %o + Alt on the S key", (modifier) => {
        press({...modifier, altKey: true})

        expect(publish).toHaveBeenCalledOnce()
    })

    it.each([
        {chord: "the save shortcut", init: {ctrlKey: true, key: "s"}},
        {chord: "Ctrl+Shift+S, kept by the browser", init: {ctrlKey: true, shiftKey: true, key: "S"}},
        {chord: "a held-down chord", init: {ctrlKey: true, altKey: true, key: "s", repeat: true}},
    ])("does not publish on $chord", ({init}) => {
        press(init)

        expect(publish).not.toHaveBeenCalled()
    })

    it.each([
        {state: "a flow the user cannot edit", flag: isReadOnly},
        {state: "a flow with errors", flag: hasErrors},
    ])("does not publish $state", ({flag}) => {
        flag.value = true

        press({ctrlKey: true, altKey: true, key: "s"})

        expect(publish).not.toHaveBeenCalled()
    })
})
