import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h, KeepAlive} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"

const saveAsDraft = vi.fn()

vi.mock("../../flows/useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({saveAsDraft}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

async function mountKeyboardSave() {
    const {useKeyboardSave} = await import("./useKeyboardSave")

    const Inner = defineComponent({
        name: "Inner",
        setup() { useKeyboardSave() },
        template: "<div />",
    })

    return mount(
        defineComponent({render: () => h(KeepAlive, null, () => h(Inner))}),
    )
}

function ctrlS() {
    document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", ctrlKey: true, bubbles: true}))
}

function metaS() {
    document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", metaKey: true, bubbles: true}))
}

describe("useKeyboardSave", () => {
    let wrapper: VueWrapper

    beforeEach(() => {
        saveAsDraft.mockClear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    it("saves on Ctrl+S, as a draft", async () => {
        wrapper = await mountKeyboardSave()
        ctrlS()
        expect(saveAsDraft).toHaveBeenCalledOnce()
    })

    it("saves on Cmd+S (metaKey), as a draft", async () => {
        wrapper = await mountKeyboardSave()
        metaS()
        expect(saveAsDraft).toHaveBeenCalledOnce()
    })

    it("leaves a Shift chord alone, even when Caps Lock reports a lowercase key", async () => {
        wrapper = await mountKeyboardSave()
        document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", ctrlKey: true, shiftKey: true, bubbles: true}))
        expect(saveAsDraft).not.toHaveBeenCalled()
    })

    it("leaves the publish chord (Ctrl+Alt+S) to Publish", async () => {
        wrapper = await mountKeyboardSave()
        document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", ctrlKey: true, altKey: true, bubbles: true}))
        expect(saveAsDraft).not.toHaveBeenCalled()
    })

    it("ignores a plain 's' keypress without a modifier", async () => {
        wrapper = await mountKeyboardSave()
        document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", bubbles: true}))
        expect(saveAsDraft).not.toHaveBeenCalled()
    })
})
