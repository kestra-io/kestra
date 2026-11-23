import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, type VNode} from "vue"
import {mount} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const {confirm} = vi.hoisted(() => ({confirm: vi.fn()}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const original = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {...original, KsMessageBox: {...original.KsMessageBox, confirm}}
})

import {KsSwitch} from "@kestra-io/design-system"
import {useExecutionDeletionDialog, type ExecutionDeletionOptions} from "./useExecutionDeletionDialog"

type BeforeClose = (action: string, instance: unknown, done: () => void) => void

const openDialog = async (offerNonTerminated: boolean, nonTerminatedCount = 0) => {
    let result: Promise<ExecutionDeletionOptions | undefined> | undefined
    i18nMount(defineComponent({
        setup() {
            result = useExecutionDeletionDialog().confirmExecutionDeletion("Delete <code>e1</code>?", {offerNonTerminated, nonTerminatedCount, total: 3})
            return () => null
        },
    }), {messages: {"execution-warn-non-terminated-count": "{count} of {total}"}})
    const [message, , options] = confirm.mock.calls[0] as [() => VNode, string, {beforeClose: BeforeClose}]
    return {result: result!, dialog: mount({render: message}), beforeClose: options.beforeClose}
}

describe("execution deletion dialog", () => {
    beforeEach(() => confirm.mockReset())

    /** Both delete paths act on the options the user left ticked, and do nothing on a dismissal. */
    it("resolves to the chosen options on confirm and to nothing when dismissed", async () => {
        let accept: (action: string) => void = () => {}
        confirm.mockReturnValueOnce(new Promise(resolve => accept = resolve))
        const confirmed = await openDialog(false)
        await confirmed.dialog.findAll("input[type=checkbox]")[1].setValue(false)
        accept("confirm")
        expect(await confirmed.result).toEqual({deleteLogs: true, deleteMetrics: false, deleteStorage: true, includeNonTerminated: false})

        confirm.mockReset().mockRejectedValueOnce("cancel")
        expect(await (await openDialog(false)).result).toBeUndefined()
    })

    /** Only the bulk delete may include running executions, so only it shows that switch. */
    it("offers the non-terminated switch only when asked", async () => {
        confirm.mockResolvedValue("confirm")

        expect((await openDialog(false)).dialog.findComponent(KsSwitch).exists()).toBe(false)
        confirm.mockClear()
        expect((await openDialog(true)).dialog.findComponent(KsSwitch).exists()).toBe(true)
    })

    it("refuses to confirm until the user consents to deleting non-terminated executions", async () => {
        confirm.mockReturnValueOnce(new Promise(() => {}))
        const {dialog, beforeClose} = await openDialog(true, 2)
        const done = vi.fn()

        expect(dialog.text()).toContain("2 of 3")
        beforeClose("confirm", undefined, done)
        expect(done).not.toHaveBeenCalled()

        beforeClose("cancel", undefined, done)
        expect(done).toHaveBeenCalledTimes(1)

        await dialog.findComponent(KsSwitch).vm.$emit("update:modelValue", true)
        beforeClose("confirm", undefined, done)
        expect(done).toHaveBeenCalledTimes(2)
    })
})
