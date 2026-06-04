import {describe, test, expect, vi, beforeEach} from "vitest"

const confirmMock = vi.fn()

vi.mock("@kestra-io/design-system", () => ({
    KsMessageBox: {confirm: (...args: unknown[]) => confirmMock(...args)},
    KsMarkdown: {},
    KsNotification: Object.assign(() => {}, {closeAll: () => {}}),
    KsTable: {},
    KsTableColumn: {},
}))

import {makeToast} from "../../../../src/utils/toast"

type Label = {key: string; value: string}

function isDirty(state: {
    inputsNoDefaults: Record<string, unknown>
    executionLabels: Label[]
    scheduleDate: string | undefined
}) {
    return (
        Object.keys(state.inputsNoDefaults).length > 0 ||
        state.executionLabels.some((label) => label.key || label.value) ||
        state.scheduleDate !== undefined
    )
}

function makeBeforeClose(dirty: () => boolean) {
    const toast = makeToast((key: string) => key)
    return (done: () => void) => {
        if (dirty()) {
            toast.confirm("discard execution confirmation", async () => {
                done()
            })
        } else {
            done()
        }
    }
}

const empty = {
    inputsNoDefaults: {},
    executionLabels: [] as Label[],
    scheduleDate: undefined as string | undefined,
}

describe("isDirty predicate", () => {
    test("pristine form is not dirty", () => {
        expect(isDirty(empty)).toBe(false)
    })

    test("a non-default input makes the form dirty", () => {
        expect(isDirty({...empty, inputsNoDefaults: {name: "value"}})).toBe(true)
    })

    test("a filled execution label makes the form dirty", () => {
        expect(isDirty({...empty, executionLabels: [{key: "env", value: "prod"}]})).toBe(true)
    })

    test("an empty label row keeps the form pristine", () => {
        expect(isDirty({...empty, executionLabels: [{key: "", value: ""}]})).toBe(false)
    })

    test("a schedule date makes the form dirty", () => {
        expect(isDirty({...empty, scheduleDate: "2026-06-04T10:00:00Z"})).toBe(true)
    })
})

describe("beforeClose discard gating", () => {
    beforeEach(() => {
        confirmMock.mockReset()
    })

    test("pristine form closes immediately without confirmation", () => {
        const done = vi.fn()
        makeBeforeClose(() => false)(done)

        expect(confirmMock).not.toHaveBeenCalled()
        expect(done).toHaveBeenCalledTimes(1)
    })

    test("dirty form asks for confirmation and closes once confirmed", async () => {
        confirmMock.mockResolvedValue(undefined)
        const done = vi.fn()

        makeBeforeClose(() => true)(done)

        expect(confirmMock).toHaveBeenCalledTimes(1)
        expect(done).not.toHaveBeenCalled()

        await Promise.resolve()
        await Promise.resolve()

        expect(done).toHaveBeenCalledTimes(1)
    })

    test("dirty form stays open when the user cancels", async () => {
        confirmMock.mockRejectedValue(new Error("cancel"))
        const done = vi.fn()

        makeBeforeClose(() => true)(done)

        await Promise.resolve()
        await Promise.resolve()

        expect(confirmMock).toHaveBeenCalledTimes(1)
        expect(done).not.toHaveBeenCalled()
    })
})
