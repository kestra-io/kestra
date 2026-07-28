import {describe, test, expect, vi, beforeEach} from "vitest"
import {defineComponent} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const confirmMock = vi.fn()

vi.mock("@kestra-io/design-system", () => ({
    KsMessageBox: {confirm: (...args: unknown[]) => confirmMock(...args)},
    KsMarkdown: {},
    KsNotification: Object.assign(() => {}, {closeAll: () => {}}),
    KsTable: {},
    KsTableColumn: {},
}))

import {useDiscardGuard} from "../../../src/composables/useDiscardGuard"

function mountGuard(isDirty: () => boolean | undefined, options?: {message?: string}) {
    let api: ReturnType<typeof useDiscardGuard>
    const Comp = defineComponent({
        setup() {
            api = useDiscardGuard(isDirty, options)
            return () => null
        },
    })
    mount(Comp, {global: {plugins: [createI18n({legacy: false, locale: "en"})]}})
    return api!
}

describe("useDiscardGuard", () => {
    beforeEach(() => {
        confirmMock.mockReset()
    })

    test("pristine state proceeds immediately without confirmation", () => {
        const {guardedClose, isConfirming} = mountGuard(() => false)
        const proceed = vi.fn()

        guardedClose(proceed)

        expect(confirmMock).not.toHaveBeenCalled()
        expect(proceed).toHaveBeenCalledTimes(1)
        expect(isConfirming.value).toBe(false)
    })

    test("dirty state asks for confirmation and proceeds once confirmed", async () => {
        confirmMock.mockResolvedValue(undefined)
        const {guardedClose, isConfirming} = mountGuard(() => true)
        const proceed = vi.fn()

        expect(isConfirming.value).toBe(false)

        guardedClose(proceed)

        expect(confirmMock).toHaveBeenCalledTimes(1)
        expect(isConfirming.value).toBe(true)
        expect(proceed).not.toHaveBeenCalled()

        await flushPromises()

        expect(proceed).toHaveBeenCalledTimes(1)
        expect(isConfirming.value).toBe(false)
    })

    test("dirty state does not proceed when the user cancels", async () => {
        confirmMock.mockRejectedValue(new Error("cancel"))
        const {guardedClose, isConfirming} = mountGuard(() => true)
        const proceed = vi.fn()

        guardedClose(proceed)
        expect(isConfirming.value).toBe(true)

        await flushPromises()

        expect(confirmMock).toHaveBeenCalledTimes(1)
        expect(proceed).not.toHaveBeenCalled()
        expect(isConfirming.value).toBe(false)
    })

    test("guard resets after cancel so a later close can confirm again", async () => {
        confirmMock.mockRejectedValueOnce(new Error("cancel")).mockResolvedValueOnce(undefined)
        const {guardedClose} = mountGuard(() => true)
        const proceed = vi.fn()

        guardedClose(proceed)
        await flushPromises()
        guardedClose(proceed)
        await flushPromises()

        expect(confirmMock).toHaveBeenCalledTimes(2)
        expect(proceed).toHaveBeenCalledTimes(1)
    })

    test("rapid repeated close attempts do not stack confirmations", () => {
        confirmMock.mockReturnValue(new Promise<void>(() => {}))
        const {guardedClose, isConfirming} = mountGuard(() => true)
        const proceed = vi.fn()

        guardedClose(proceed)
        expect(isConfirming.value).toBe(true)
        guardedClose(proceed)

        expect(confirmMock).toHaveBeenCalledTimes(1)
    })

    test("passes a custom message to the confirmation", () => {
        confirmMock.mockReturnValue(new Promise<void>(() => {}))
        const {guardedClose} = mountGuard(() => true, {message: "Custom discard?"})

        guardedClose(vi.fn())

        expect(confirmMock).toHaveBeenCalledWith("Custom discard?", expect.anything(), expect.objectContaining({type: "warning", showCancelButton: true}))
    })
})
