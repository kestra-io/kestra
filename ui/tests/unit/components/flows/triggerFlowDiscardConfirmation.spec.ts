import {describe, test, expect, vi} from "vitest"

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

function makeBeforeClose(dirty: () => boolean, confirm: () => Promise<void>) {
    let isConfirmingClose = false
    return (done: () => void) => {
        if (!dirty()) {
            done()
            return
        }
        if (isConfirmingClose) {
            return
        }
        isConfirmingClose = true
        confirm()
            .then(() => done())
            .catch(() => {})
            .finally(() => {
                isConfirmingClose = false
            })
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
    test("pristine form closes immediately without confirmation", () => {
        const confirm = vi.fn(() => Promise.resolve())
        const done = vi.fn()
        makeBeforeClose(() => false, confirm)(done)

        expect(confirm).not.toHaveBeenCalled()
        expect(done).toHaveBeenCalledTimes(1)
    })

    test("dirty form asks for confirmation and closes once confirmed", async () => {
        const confirm = vi.fn(() => Promise.resolve())
        const done = vi.fn()

        makeBeforeClose(() => true, confirm)(done)

        expect(confirm).toHaveBeenCalledTimes(1)
        expect(done).not.toHaveBeenCalled()

        await Promise.resolve()
        await Promise.resolve()

        expect(done).toHaveBeenCalledTimes(1)
    })

    test("dirty form stays open when the user cancels", async () => {
        const confirm = vi.fn(() => Promise.reject(new Error("cancel")))
        const done = vi.fn()

        makeBeforeClose(() => true, confirm)(done)

        await Promise.resolve()
        await Promise.resolve()

        expect(confirm).toHaveBeenCalledTimes(1)
        expect(done).not.toHaveBeenCalled()
    })

    test("rapid repeated close attempts do not stack confirmations", () => {
        const confirm = vi.fn(() => new Promise<void>(() => {}))
        const done = vi.fn()
        const beforeClose = makeBeforeClose(() => true, confirm)

        beforeClose(done)
        beforeClose(done)

        expect(confirm).toHaveBeenCalledTimes(1)
    })
})
