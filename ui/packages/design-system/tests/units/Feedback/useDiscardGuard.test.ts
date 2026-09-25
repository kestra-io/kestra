import {describe, test, expect, vi, beforeEach} from "vitest"
import {defineComponent} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {ElMessageBox} from "element-plus"
import {useDiscardGuard} from "../../../src/composables/useDiscardGuard"

vi.mock("element-plus", () => ({
    ElMessageBox: Object.assign(vi.fn(), {alert: vi.fn(), confirm: vi.fn(), prompt: vi.fn(), close: vi.fn()}),
}))

const confirmMock = vi.mocked(ElMessageBox.confirm)

function mountGuard(isDirty: () => boolean | undefined) {
    let api: ReturnType<typeof useDiscardGuard>
    const Comp = defineComponent({
        setup() {
            api = useDiscardGuard(isDirty)
            return () => null
        },
    })
    mount(Comp)
    return api!
}

describe("useDiscardGuard", () => {
    beforeEach(() => {
        confirmMock.mockReset()
    })

    test("proceeds only once the user confirms", async () => {
        confirmMock.mockResolvedValue("confirm")
        const {guardedClose} = mountGuard(() => true)
        const proceed = vi.fn()

        guardedClose(proceed)
        expect(proceed).not.toHaveBeenCalled()

        await flushPromises()

        expect(proceed).toHaveBeenCalledTimes(1)
    })

    test("does not proceed when the user cancels", async () => {
        confirmMock.mockRejectedValue(new Error("cancel"))
        const {guardedClose} = mountGuard(() => true)
        const proceed = vi.fn()

        guardedClose(proceed)
        await flushPromises()

        expect(proceed).not.toHaveBeenCalled()
    })

    test("does not stack confirmations, and asks again after a cancel", async () => {
        confirmMock.mockRejectedValueOnce(new Error("cancel")).mockResolvedValueOnce("confirm")
        const {guardedClose} = mountGuard(() => true)
        const proceed = vi.fn()

        guardedClose(proceed)
        guardedClose(proceed)
        expect(confirmMock).toHaveBeenCalledTimes(1)

        await flushPromises()
        guardedClose(proceed)
        await flushPromises()

        expect(confirmMock).toHaveBeenCalledTimes(2)
        expect(proceed).toHaveBeenCalledTimes(1)
    })
})
