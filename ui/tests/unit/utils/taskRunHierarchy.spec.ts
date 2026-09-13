import {describe, it, expect} from "vitest"

import {buildTaskRunHierarchy} from "../../../src/utils/taskRunHierarchy"

type TestTaskRun = {id: string; parentTaskRunId?: string; start?: number}

const flatten = (taskRunList: TestTaskRun[], compareSiblings?: (a: TestTaskRun, b: TestTaskRun) => number) =>
    buildTaskRunHierarchy(taskRunList, compareSiblings).map(({task, depth}) => [task.id, depth])

describe("buildTaskRunHierarchy", () => {
    it("returns an empty array for an empty list", () => {
        expect(buildTaskRunHierarchy([] as TestTaskRun[])).toEqual([])
    })

    it("treats an orphan child (parent not in list) as a depth-0 root", () => {
        // A LOOP iteration execution: every taskrun points at the Loop run, which is absent from the list.
        const taskRunList: TestTaskRun[] = [
            {id: "log-a", parentTaskRunId: "loop-run-not-in-list"},
            {id: "log-b", parentTaskRunId: "loop-run-not-in-list"},
        ]

        expect(flatten(taskRunList)).toEqual([
            ["log-a", 0],
            ["log-b", 0],
        ])
    })

    it("nests present children under their parent with correct depth", () => {
        const taskRunList: TestTaskRun[] = [
            {id: "parent"},
            {id: "child", parentTaskRunId: "parent"},
            {id: "root"},
        ]

        expect(flatten(taskRunList)).toEqual([
            ["parent", 0],
            ["child", 1],
            ["root", 0],
        ])
    })

    it("handles multi-level nesting", () => {
        const taskRunList: TestTaskRun[] = [
            {id: "a"},
            {id: "b", parentTaskRunId: "a"},
            {id: "c", parentTaskRunId: "b"},
        ]

        expect(flatten(taskRunList)).toEqual([
            ["a", 0],
            ["b", 1],
            ["c", 2],
        ])
    })

    it("sorts siblings with the provided comparator (Gantt's start-date contract)", () => {
        const taskRunList: TestTaskRun[] = [
            {id: "late", start: 200},
            {id: "early", start: 100},
            {id: "late-child", parentTaskRunId: "early", start: 50},
            {id: "early-child", parentTaskRunId: "early", start: 10},
        ]

        expect(flatten(taskRunList, (a, b) => (a.start ?? 0) - (b.start ?? 0))).toEqual([
            ["early", 0],
            ["early-child", 1],
            ["late-child", 1],
            ["late", 0],
        ])
    })

    it("preserves list order when no comparator is given (Logs' behaviour)", () => {
        const taskRunList: TestTaskRun[] = [
            {id: "third"},
            {id: "first"},
            {id: "second", parentTaskRunId: "third"},
        ]

        expect(flatten(taskRunList)).toEqual([
            ["third", 0],
            ["second", 1],
            ["first", 0],
        ])
    })
})
