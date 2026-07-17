import {describe, it, expect} from "vitest"

import {computeTaskBarPercents, type TaskBarInput} from "../../../src/utils/ganttSeries"

describe("computeTaskBarPercents", () => {
    it("does not stretch a quick task's bar to its long-running sibling's end inside a Sequential block", () => {
        const executionStart = 0
        const executionDelta = 10470 

        const tasks: TaskBarInput[] = [
            {id: "block", startTs: 0, stopTs: 10470},
            {id: "quick_task", parentTaskRunId: "block", startTs: 0, stopTs: 300},
            {id: "slow_task", parentTaskRunId: "block", startTs: 300, stopTs: 10300},
            {id: "fail_task", parentTaskRunId: "block", startTs: 10300, stopTs: 10470},
        ]

        const result = computeTaskBarPercents(tasks, executionStart, executionDelta)
        const byId = Object.fromEntries(result.map((r) => [r.id, r]))

        expect(byId.quick_task.parentEndPercent).toBeUndefined()
        expect(byId.quick_task.start).toBeCloseTo(0, 5)
        expect(byId.quick_task.width).toBeCloseTo((300 / 10470) * 100, 5)

        expect(byId.fail_task.parentEndPercent).toBeCloseTo(100, 5)
    })

    it("does not let sibling start-order affect alignment inside a Parallel block", () => {
        const executionStart = 0
        const executionDelta = 8560 

        const tasks: TaskBarInput[] = [
            {id: "block", startTs: 0, stopTs: 8560},
            {id: "quick_task", parentTaskRunId: "block", startTs: 20, stopTs: 550},
            {id: "slow_task", parentTaskRunId: "block", startTs: 10, stopTs: 8410},
            {id: "medium_task", parentTaskRunId: "block", startTs: 0, stopTs: 4390},
        ]

        const result = computeTaskBarPercents(tasks, executionStart, executionDelta)
        const byId = Object.fromEntries(result.map((r) => [r.id, r]))

        expect(byId.quick_task.parentEndPercent).toBeUndefined()
        expect(byId.medium_task.parentEndPercent).toBeUndefined()
        expect(byId.slow_task.parentEndPercent).toBeDefined()
    })

    it("aligns a single child's bar with its parent's end (nested-alignment case)", () => {

        const executionStart = 0
        const executionDelta = 240

        const tasks: TaskBarInput[] = [
            {id: "block", startTs: 0, stopTs: 240},
            {id: "only_task", parentTaskRunId: "block", startTs: 0, stopTs: 240},
        ]

        const result = computeTaskBarPercents(tasks, executionStart, executionDelta)
        const byId = Object.fromEntries(result.map((r) => [r.id, r]))

        expect(byId.only_task.parentEndPercent).toBeCloseTo(100, 5)
    })

    it("does not align when the parent task run is not present in the input list (orphan)", () => {
        const tasks: TaskBarInput[] = [
            {id: "child", parentTaskRunId: "missing-parent", startTs: 0, stopTs: 100},
        ]

        const result = computeTaskBarPercents(tasks, 0, 100)

        expect(result[0].parentEndPercent).toBeUndefined()
    })

    it("respects a custom alignment epsilon", () => {
        const tasks: TaskBarInput[] = [
            {id: "block", startTs: 0, stopTs: 1000},
            {id: "child", parentTaskRunId: "block", startTs: 0, stopTs: 950},
        ]

        expect(
            computeTaskBarPercents(tasks, 0, 1000, 10)[1].parentEndPercent,
        ).toBeUndefined() 

        expect(
            computeTaskBarPercents(tasks, 0, 1000, 100)[1].parentEndPercent,
        ).toBeCloseTo(100, 5) 
    })
})