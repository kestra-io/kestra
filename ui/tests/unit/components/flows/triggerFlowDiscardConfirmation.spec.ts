import {describe, test, expect} from "vitest"

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

const empty = {
    inputsNoDefaults: {},
    executionLabels: [] as Label[],
    scheduleDate: undefined as string | undefined,
}

describe("FlowRun isDirty predicate", () => {
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
