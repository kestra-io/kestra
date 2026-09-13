import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h, KeepAlive} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"

const saveMock = vi.fn()

vi.mock("../../../src/components/flows/useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({save: saveMock}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

async function mountKeyboardSave() {
    const {useKeyboardSave} = await import("../../../src/components/no-code/utils/useKeyboardSave")

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
        saveMock.mockClear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    it("delegates Ctrl+S to the shared save action", async () => {
        wrapper = await mountKeyboardSave()
        ctrlS()
        expect(saveMock).toHaveBeenCalledOnce()
    })

    it("delegates Cmd+S (metaKey) to the shared save action", async () => {
        wrapper = await mountKeyboardSave()
        metaS()
        expect(saveMock).toHaveBeenCalledOnce()
    })

    it("ignores a plain 's' keypress without a modifier", async () => {
        wrapper = await mountKeyboardSave()
        document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", bubbles: true}))
        expect(saveMock).not.toHaveBeenCalled()
    })
})
