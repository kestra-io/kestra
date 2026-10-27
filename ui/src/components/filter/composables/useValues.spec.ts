import {describe, it, expect, vi} from "vitest"

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition: "OSS"}}),
}))

import {useValues} from "./useValues"

describe("useValues EXECUTION_STATES", () => {
    const {VALUES} = useValues("executions", ((key: string) => key) as any)
    const states = VALUES.EXECUTION_STATES.map((option: {value: unknown}) => option.value)

    it("does not offer ACCEPTED, which only exists on task runs and is rejected by the executions state column", () => {
        expect(states).not.toContain("ACCEPTED")
    })

    it("keeps offering the other states", () => {
        expect(states).toEqual(expect.arrayContaining(["SUBMITTED", "RUNNING", "SUCCESS", "FAILED"]))
    })
})
