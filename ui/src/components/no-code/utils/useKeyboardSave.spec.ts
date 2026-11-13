import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h, KeepAlive} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"

const saveKeepingDraftState = vi.fn()

vi.mock("../../flows/useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({saveKeepingDraftState}),
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
        saveKeepingDraftState.mockClear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    it("saves on Ctrl+S, keeping a draft flow a draft", async () => {
        wrapper = await mountKeyboardSave()
        ctrlS()
        expect(saveKeepingDraftState).toHaveBeenCalledOnce()
    })

    it("saves on Cmd+S (metaKey), keeping a draft flow a draft", async () => {
        wrapper = await mountKeyboardSave()
        metaS()
        expect(saveKeepingDraftState).toHaveBeenCalledOnce()
    })

    it("ignores a plain 's' keypress without a modifier", async () => {
        wrapper = await mountKeyboardSave()
        document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", bubbles: true}))
        expect(saveKeepingDraftState).not.toHaveBeenCalled()
    })
})
