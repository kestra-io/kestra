import {beforeEach, describe, expect, it, vi} from "vitest"

const store = vi.hoisted(() => ({taskRunSelections: new Map<string, string>()}))

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => ({
        taskRunSelections: {
            get: (key: string) => store.taskRunSelections.get(key),
            set: (key: string, value: string) => store.taskRunSelections.set(key, value),
        },
    }),
}))

import {useSelectedTaskRun} from "./useSelectedTaskRun"
import type {Execution} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import type {TaskRunAttempt} from "@kestra-io/kestra-sdk"

function attempt(current: TaskRunAttempt["state"]["current"]): TaskRunAttempt {
    return {state: {current, histories: []}}
}

function taskRun(overrides: Partial<ExecutionTaskRun> = {}): ExecutionTaskRun {
    return {
        id: "run-1",
        taskId: "my-task",
        state: {current: "SUCCESS", histories: []},
        ...overrides,
    }
}

function execution(overrides: Partial<Execution> = {}): Execution {
    return {
        id: "exec-1",
        namespace: "my.namespace",
        flowId: "my-flow",
        flowRevision: 1,
        state: {current: "SUCCESS", histories: []},
        ...overrides,
    }
}

beforeEach(() => {
    store.taskRunSelections.clear()
})

describe("currentTaskRuns", () => {
    it("keeps only the runs matching the current taskId", () => {
        const run = taskRun({id: "run-a", taskId: "target"})
        const other = taskRun({id: "run-b", taskId: "other"})
        const {currentTaskRuns} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => run,
            taskRuns: () => [run, other],
        })

        expect(currentTaskRuns.value).toEqual([run])
    })
})

describe("selectedTaskRunId", () => {
    it("falls back to the passed task run id when no stored selection and no matching runs", () => {
        const run = taskRun({id: "run-a", taskId: "target"})
        const {selectedTaskRunId} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => run,
            taskRuns: () => [],
        })

        expect(selectedTaskRunId.value).toBe("run-a")
    })

    it("falls back to the first matching run when nothing is stored", () => {
        const first = taskRun({id: "run-a", taskId: "target"})
        const second = taskRun({id: "run-b", taskId: "target"})
        const other = taskRun({id: "run-c", taskId: "other"})
        const {selectedTaskRunId} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => first,
            taskRuns: () => [first, second, other],
        })

        expect(selectedTaskRunId.value).toBe("run-a")
    })

    it("stored selection wins over both fallbacks", () => {
        const run = taskRun({id: "run-a", taskId: "target"})
        const other = taskRun({id: "run-b", taskId: "target"})
        store.taskRunSelections.set("exec-1|target", "run-b")
        const {selectedTaskRunId} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => run,
            taskRuns: () => [run, other],
        })

        expect(selectedTaskRunId.value).toBe("run-b")
    })

    it("writes the selected id under the execution+task key and a different task does not read it back", () => {
        const run = taskRun({id: "run-a", taskId: "target"})
        const unrelated = taskRun({id: "run-x", taskId: "other"})
        const {selectedTaskRunId} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => run,
            taskRuns: () => [run],
        })
        selectedTaskRunId.value = "run-a"

        expect(store.taskRunSelections.get("exec-1|target")).toBe("run-a")
        const {selectedTaskRunId: otherId} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => unrelated,
            taskRuns: () => [unrelated],
        })
        expect(otherId.value).toBe("run-x")
    })
})

describe("currentTaskRun", () => {
    it("resolves the selected id from the matching runs", () => {
        const run = taskRun({id: "run-a", taskId: "target"})
        const other = taskRun({id: "run-b", taskId: "target"})
        store.taskRunSelections.set("exec-1|target", "run-b")
        const {currentTaskRun} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => run,
            taskRuns: () => [run, other],
        })

        expect(currentTaskRun.value).toBe(other)
    })

    it("falls back to the passed run when the selected id matches nothing", () => {
        const run = taskRun({id: "run-a", taskId: "target"})
        store.taskRunSelections.set("exec-1|target", "run-gone")
        const {currentTaskRun} = useSelectedTaskRun({
            execution: () => execution(),
            taskRun: () => run,
            taskRuns: () => [run],
        })

        expect(currentTaskRun.value).toBe(run)
    })
})

describe("currentTaskRunWithExecution", () => {
    it("adds execution id, namespace and flow id to the current task run", () => {
        const run = taskRun({id: "run-a", taskId: "my-task"})
        const exec = execution({id: "exec-42", namespace: "acme.prod", flowId: "deploy"})
        const {currentTaskRunWithExecution} = useSelectedTaskRun({
            execution: () => exec,
            taskRun: () => run,
            taskRuns: () => [run],
        })

        expect(currentTaskRunWithExecution.value).toMatchObject({
            executionId: "exec-42",
            namespace: "acme.prod",
            flowId: "deploy",
        })
    })
})

describe("attempts", () => {
    it("returns the full attempt list while the execution is RUNNING even when a forced attempt number is set", () => {
        const attempt0 = attempt("RUNNING")
        const attempt1 = attempt("SUCCESS")
        const run = taskRun({taskId: "t", attempts: [attempt0, attempt1]})
        const exec = execution({state: {current: "RUNNING", histories: []}})
        const {selectedAttempt, currentAttemptIndex} = useSelectedTaskRun({
            execution: () => exec,
            taskRun: () => run,
            taskRuns: () => [run],
            forcedAttemptNumber: () => 0,
            attemptIndex: () => 1,
        })

        expect(currentAttemptIndex.value).toBe(1)
        expect(selectedAttempt.value).toBe(attempt1)
    })

    it("honours the forced attempt number when the execution is not RUNNING", () => {
        const attempt0 = attempt("FAILED")
        const attempt1 = attempt("SUCCESS")
        const run = taskRun({taskId: "t", attempts: [attempt0, attempt1]})
        const exec = execution({state: {current: "SUCCESS", histories: []}})
        const {selectedAttempt} = useSelectedTaskRun({
            execution: () => exec,
            taskRun: () => run,
            taskRuns: () => [run],
            forcedAttemptNumber: () => 0,
        })

        expect(selectedAttempt.value).toBe(attempt0)
    })

    it("returns a synthetic attempt from the task run state when attempts is absent", () => {
        const run = taskRun({taskId: "t", attempts: undefined})
        const exec = execution({state: {current: "RUNNING", histories: []}})
        const {selectedAttempt} = useSelectedTaskRun({
            execution: () => exec,
            taskRun: () => run,
            taskRuns: () => [run],
        })

        expect(selectedAttempt.value).toEqual({state: run.state})
    })
})

describe("currentAttemptIndex", () => {
    it("clamps the attempt index to the last available attempt when it exceeds the list", () => {
        const attempts = [attempt("FAILED"), attempt("SUCCESS")]
        const run = taskRun({attempts})
        const exec = execution({state: {current: "SUCCESS", histories: []}})
        const {currentAttemptIndex} = useSelectedTaskRun({
            execution: () => exec,
            taskRun: () => run,
            taskRuns: () => [run],
            attemptIndex: () => 99,
        })

        expect(currentAttemptIndex.value).toBe(1)
    })

    it("clamps the attempt index to zero when it would go negative (empty list via forced attempt on non-running)", () => {
        const run = taskRun({attempts: undefined})
        const exec = execution({state: {current: "SUCCESS", histories: []}})
        const {currentAttemptIndex} = useSelectedTaskRun({
            execution: () => exec,
            taskRun: () => run,
            taskRuns: () => [run],
            forcedAttemptNumber: () => 0,
        })

        expect(currentAttemptIndex.value).toBe(0)
    })
})
