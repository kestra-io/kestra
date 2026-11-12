import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, type VNode} from "vue"
import {mount} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const {confirm} = vi.hoisted(() => ({confirm: vi.fn()}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const original = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {...original, KsMessageBox: {...original.KsMessageBox, confirm}}
})

import {KsAlert, KsSwitch} from "@kestra-io/design-system"
import {useExecutionDeletionDialog, type ExecutionDeletionOptions} from "./useExecutionDeletionDialog"

interface MessageBoxOptionsStub {
    confirmButtonDisabled?: boolean;
    beforeClose?: (action: string, instance: {confirmButtonDisabled?: boolean}, done: () => void) => void;
}

const openDialog = async (offerNonTerminated: boolean, nonTerminatedCount = 0) => {
    let result: Promise<ExecutionDeletionOptions | undefined> | undefined
    i18nMount(defineComponent({
        setup() {
            result = useExecutionDeletionDialog().confirmExecutionDeletion("Delete <code>e1</code>?", {
                offerNonTerminated,
                nonTerminatedCount,
                total: nonTerminatedCount,
            })
            return () => null
        },
    }))
    const lastCall = confirm.mock.calls[confirm.mock.calls.length - 1]
    const message = lastCall[0] as () => VNode
    const options = lastCall[2] as MessageBoxOptionsStub
    return {result: result!, dialog: mount({render: message}), options}
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

    it("requires consent when non-terminated executions are selected", async () => {
        confirm.mockResolvedValue("confirm")

        const withoutConsent = await openDialog(true, 0)
        expect(withoutConsent.options.confirmButtonDisabled).toBe(false)
        expect(withoutConsent.dialog.findComponent(KsAlert).exists()).toBe(false)

        const withConsent = await openDialog(true, 2)
        expect(withConsent.options.confirmButtonDisabled).toBe(true)
        expect(withConsent.dialog.findComponent(KsAlert).exists()).toBe(true)
        expect(withConsent.dialog.findComponent(KsAlert).props("description")).toContain("execution-warn-non-terminated-count")

        // beforeClose blocks confirm while switch is false
        let doneCalled = false
        const fakeState = {confirmButtonDisabled: false}
        withConsent.options.beforeClose?.("confirm", fakeState, () => {
            doneCalled = true
        })
        expect(doneCalled).toBe(false)
        expect(fakeState.confirmButtonDisabled).toBe(true)

        // beforeClose allows confirm once switch is toggled on
        await withConsent.dialog.findComponent(KsSwitch).vm.$emit("update:modelValue", true)
        withConsent.options.beforeClose?.("confirm", fakeState, () => {
            doneCalled = true
        })
        expect(doneCalled).toBe(true)
    })
})
