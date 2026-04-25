import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h, KeepAlive} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"

const saveWithDefaultActionMock = vi.fn()

vi.mock("../../../src/stores/flow", () => ({
    useFlowStore: () => ({saveWithDefaultAction: saveWithDefaultActionMock}),
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

    // onActivated fires when the component is first mounted inside KeepAlive.
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
        localStorage.clear()
        saveWithDefaultActionMock.mockClear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        // Unmounting triggers onDeactivated, which removes the document listener.
        wrapper?.unmount()
    })

    // Ctrl+S delegates to the store's single source of truth for the default save action
    // (Save vs Save as draft); the composable itself no longer resolves the preference.
    it("delegates Ctrl+S to saveWithDefaultAction", async () => {
        wrapper = await mountKeyboardSave()
        ctrlS()
        expect(saveWithDefaultActionMock).toHaveBeenCalledOnce()
    })

    it("delegates Cmd+S (metaKey) to saveWithDefaultAction", async () => {
        wrapper = await mountKeyboardSave()
        metaS()
        expect(saveWithDefaultActionMock).toHaveBeenCalledOnce()
    })

    it("ignores a plain 's' keypress without a modifier", async () => {
        wrapper = await mountKeyboardSave()
        document.dispatchEvent(new KeyboardEvent("keydown", {key: "s", bubbles: true}))
        expect(saveWithDefaultActionMock).not.toHaveBeenCalled()
    })
})
