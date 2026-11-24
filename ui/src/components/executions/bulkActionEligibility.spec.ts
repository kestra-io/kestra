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
        breakpoints: state === State.BREAKPOINT ? [{id: "t1"}] : undefined,
    } as unknown as Execution
}

describe("bulk action eligibility rules", () => {
    it("treats a PAUSED execution as not terminated", () => {
        expect(isExecutionTerminated(makeExecution(State.PAUSED))).toBe(false)
        expect(isExecutionTerminated(makeExecution(State.RUNNING))).toBe(false)
        expect(isExecutionTerminated(makeExecution(State.SUCCESS))).toBe(true)
        expect(isExecutionTerminated(makeExecution(State.KILLED))).toBe(true)
    })

    it("refuses to resume a BREAKPOINT execution without breakpoints", () => {
        const execution = {...makeExecution(State.BREAKPOINT), breakpoints: []}

        expect(bulkActionRules[action.RESUME].allowed(execution)).toBe(false)
    })

    it.each([
        [action.UPDATE, [State.SUCCESS, State.FAILED, State.WARNING, State.CANCELLED], [State.KILLED, State.RUNNING, State.PAUSED, State.QUEUED]],
        [action.RESTART, [State.FAILED], [State.SUCCESS, State.RUNNING, State.KILLED]],
        [action.REPLAY, [State.SUCCESS, State.FAILED, State.KILLED], [State.RUNNING, State.PAUSED]],
        [action.KILL, [State.RUNNING, State.PAUSED, State.QUEUED], [State.SUCCESS, State.FAILED, State.KILLED]],
        [action.PAUSE, [State.RUNNING, State.PAUSING, State.KILLING], [State.PAUSED, State.SUCCESS]],
        [action.RESUME, [State.PAUSED, State.BREAKPOINT], [State.RUNNING, State.SUCCESS]],
        [action.UNQUEUE, [State.QUEUED], [State.RUNNING, State.SUCCESS]],
        [action.FORCE_RUN, [State.RUNNING, State.QUEUED, State.PAUSED], [State.SUCCESS, State.KILLED]],
        [action.CHANGE_LABELS, [State.SUCCESS, State.FAILED], [State.RUNNING, State.PAUSED]],
    ])("%s accepts %j and refuses %j", (executionAction, allowed, refused) => {
        const rule = bulkActionRules[executionAction]

        for (const state of allowed) {
            expect(rule.allowed(makeExecution(state)), state).toBe(true)
        }
        for (const state of refused) {
            expect(rule.allowed(makeExecution(state)), state).toBe(false)
        }
    })
})
