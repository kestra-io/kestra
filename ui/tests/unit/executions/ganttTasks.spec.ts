import {describe, expect, it} from "vitest"

import {buildGanttTasks, type GanttTaskRun} from "../../../src/components/executions/ganttTasks"

const taskRun = (id: string, date: string, parentTaskRunId?: string): GanttTaskRun => ({
    id,
    parentTaskRunId,
    state: {
        histories: [{date}],
    },
})

describe("buildGanttTasks", () => {
    it("keeps loop sub-execution task runs whose parent task run belongs to another execution", () => {
        const tasks = buildGanttTasks([
            taskRun("hello", "2026-06-22T10:00:00Z", "loop-parent-task-run"),
        ])

        expect(tasks).toHaveLength(1)
        expect(tasks[0].task.id).toBe("hello")
        expect(tasks[0].depth).toBe(0)
    })

    it("nests task runs when the parent is present in the same execution", () => {
        const tasks = buildGanttTasks([
            taskRun("child", "2026-06-22T10:01:00Z", "parent"),
            taskRun("parent", "2026-06-22T10:00:00Z"),
        ])

        expect(tasks).toHaveLength(2)
        expect(tasks[0].task.id).toBe("parent")
        expect(tasks[1].task.id).toBe("child")
        expect(tasks[1].depth).toBe(1)
    })
})