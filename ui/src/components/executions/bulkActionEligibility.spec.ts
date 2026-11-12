import {describe, expect, it} from "vitest"
import {State} from "@kestra-io/design-system"
import action from "../../models/action"
import type {Execution} from "../../stores/executions"
import {bulkActionRules, isExecutionTerminated} from "./bulkActionEligibility"

function makeExecution(state: string): Execution {
    return {
        id: `exec-${state.toLowerCase()}`,
        namespace: "test-ns",
        flowId: "test-flow",
        flowRevision: 1,
        state: {
            current: state,
            histories: [],
        },
    } as unknown as Execution
}

describe("bulk action eligibility rules", () => {
    const terminatedStates = [
        State.SUCCESS,
        State.WARNING,
        State.FAILED,
        State.KILLED,
        State.CANCELLED,
        State.RETRIED,
        State.SKIPPED,
        State.RESUBMITTED,
    ]

    const nonTerminatedStates = [
        State.RUNNING,
        State.PAUSED,
        State.QUEUED,
        State.CREATED,
        State.RESTARTED,
    ]

    describe("isExecutionTerminated", () => {
        it("returns true for terminated states", () => {
            for (const st of terminatedStates) {
                expect(isExecutionTerminated(makeExecution(st))).toBe(true)
            }
        })

        it("returns false for non-terminated states", () => {
            for (const st of nonTerminatedStates) {
                expect(isExecutionTerminated(makeExecution(st))).toBe(false)
            }
        })
    })

    describe("UPDATE (change state)", () => {
        const rule = bulkActionRules[action.UPDATE]

        it("allows terminated executions except KILLED", () => {
            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(true)
            expect(rule.allowed(makeExecution(State.FAILED))).toBe(true)
            expect(rule.allowed(makeExecution(State.WARNING))).toBe(true)
            expect(rule.allowed(makeExecution(State.CANCELLED))).toBe(true)

            // KILLED is not eligible
            expect(rule.allowed(makeExecution(State.KILLED))).toBe(false)

            // Non-terminated are not eligible
            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(false)
            expect(rule.allowed(makeExecution(State.PAUSED))).toBe(false)
            expect(rule.allowed(makeExecution(State.QUEUED))).toBe(false)
        })

        it("has expected tooltip key", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible change state")
        })
    })

    describe("RESTART", () => {
        const rule = bulkActionRules[action.RESTART]

        it("allows only FAILED executions", () => {
            expect(rule.allowed(makeExecution(State.FAILED))).toBe(true)

            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(false)
            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(false)
            expect(rule.allowed(makeExecution(State.KILLED))).toBe(false)
        })

        it("has expected tooltip key and params", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible not state")
            expect(rule.tooltipParams).toEqual({state: State.FAILED})
        })
    })

    describe("REPLAY", () => {
        const rule = bulkActionRules[action.REPLAY]

        it("allows only terminated executions", () => {
            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(true)
            expect(rule.allowed(makeExecution(State.FAILED))).toBe(true)
            expect(rule.allowed(makeExecution(State.KILLED))).toBe(true)

            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(false)
            expect(rule.allowed(makeExecution(State.PAUSED))).toBe(false)
        })

        it("has expected tooltip key", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible not terminated")
        })
    })

    describe("KILL", () => {
        const rule = bulkActionRules[action.KILL]

        it("allows only non-terminated executions", () => {
            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(true)
            expect(rule.allowed(makeExecution(State.PAUSED))).toBe(true)
            expect(rule.allowed(makeExecution(State.QUEUED))).toBe(true)

            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(false)
            expect(rule.allowed(makeExecution(State.FAILED))).toBe(false)
            expect(rule.allowed(makeExecution(State.KILLED))).toBe(false)
        })

        it("has expected tooltip key", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible already terminated")
        })
    })

    describe("PAUSE", () => {
        const rule = bulkActionRules[action.PAUSE]

        it("allows only RUNNING executions", () => {
            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(true)

            expect(rule.allowed(makeExecution(State.PAUSED))).toBe(false)
            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(false)
        })

        it("has expected tooltip key and params", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible not state")
            expect(rule.tooltipParams).toEqual({state: State.RUNNING})
        })
    })

    describe("RESUME", () => {
        const rule = bulkActionRules[action.RESUME]

        it("allows only PAUSED executions", () => {
            expect(rule.allowed(makeExecution(State.PAUSED))).toBe(true)

            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(false)
            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(false)
        })

        it("has expected tooltip key and params", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible not state")
            expect(rule.tooltipParams).toEqual({state: State.PAUSED})
        })
    })

    describe("UNQUEUE", () => {
        const rule = bulkActionRules[action.UNQUEUE]

        it("allows only QUEUED executions", () => {
            expect(rule.allowed(makeExecution(State.QUEUED))).toBe(true)

            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(false)
            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(false)
        })

        it("has expected tooltip key and params", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible not state")
            expect(rule.tooltipParams).toEqual({state: State.QUEUED})
        })
    })

    describe("FORCE_RUN", () => {
        const rule = bulkActionRules[action.FORCE_RUN]

        it("allows only non-terminated executions", () => {
            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(true)
            expect(rule.allowed(makeExecution(State.QUEUED))).toBe(true)

            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(false)
            expect(rule.allowed(makeExecution(State.KILLED))).toBe(false)
        })

        it("has expected tooltip key", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible already terminated")
        })
    })

    describe("CHANGE_LABELS", () => {
        const rule = bulkActionRules[action.CHANGE_LABELS]

        it("allows only terminated executions", () => {
            expect(rule.allowed(makeExecution(State.SUCCESS))).toBe(true)
            expect(rule.allowed(makeExecution(State.FAILED))).toBe(true)

            expect(rule.allowed(makeExecution(State.RUNNING))).toBe(false)
        })

        it("has expected tooltip key", () => {
            expect(rule.tooltipKey).toBe("bulk action ineligible not terminated")
        })
    })
})

