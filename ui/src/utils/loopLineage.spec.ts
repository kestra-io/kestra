import {describe, expect, it, vi} from "vitest"
import {createI18n} from "vue-i18n"
import en from "../translations/en.json"
import {
    executionLineage,
    iterationsListFilters,
    iterationsListLineage,
    resolveLoopLineage,
    type LineageExecution,
    type LoopLineageDeps,
    type LoopLineageLevel,
} from "./loopLineage"

vi.mock("../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs: vi.fn()}))

const {t} = createI18n({legacy: false, locale: "en", messages: en}).global
const context = {namespace: "company.team", flowId: "nested_loop_demo", t: t as (key: string, named?: Record<string, unknown>) => string}

const execution = (id: string, parentId?: string, loopRun?: LineageExecution["loopRun"]): LineageExecution => ({id, parentId, loopRun})

function depsFor(executions: LineageExecution[], counts: Record<string, number> = {}): LoopLineageDeps {
    return {
        fetchExecution: async (id) => {
            const found = executions.find((candidate) => candidate.id === id)
            if (!found) throw new Error("403")
            return found
        },
        fetchIterationCount: async (_executionId, taskRunId) => counts[taskRunId],
    }
}

const root = execution("root-execution")
const outer = execution("1mubyrnLabcdef", "root-execution", {taskId: "per_region", taskRunId: "tr-region", index: 0, value: "EMEA"})
const inner = execution("5j2D9NuQvgj0wiyuADhgmt", "1mubyrnLabcdef", {taskId: "per_quarter", taskRunId: "tr-quarter", index: 1, value: "Q2"})

describe("resolveLoopLineage", () => {
    it("returns no level for a top-level execution without fetching anything", async () => {
        const deps = depsFor([root])
        const fetchExecution = vi.spyOn(deps, "fetchExecution")

        expect(await resolveLoopLineage(root, deps)).toEqual([])
        expect(fetchExecution).not.toHaveBeenCalled()
    })

    it("returns no level for a subflow child that has a parent but no loop run", async () => {
        expect(await resolveLoopLineage(execution("child", "root-execution"), depsFor([root]))).toEqual([])
    })

    it("resolves one level with the count read from the parent's loop task run", async () => {
        const levels = await resolveLoopLineage(outer, depsFor([root, outer], {"tr-region": 3}))

        expect(levels).toEqual([
            {executionId: "1mubyrnLabcdef", parentExecutionId: "root-execution", taskId: "per_region", number: 1, count: 3, value: "EMEA"},
        ])
    })

    it("orders nested levels from the outermost to the current iteration", async () => {
        const levels = await resolveLoopLineage(inner, depsFor([root, outer, inner], {"tr-region": 3, "tr-quarter": 4}))

        expect(levels.map(({taskId, number, count, value}) => ({taskId, number, count, value}))).toEqual([
            {taskId: "per_region", number: 1, count: 3, value: "EMEA"},
            {taskId: "per_quarter", number: 2, count: 4, value: "Q2"},
        ])
    })

    it("leaves the count undefined when the loop has not published it", async () => {
        const [level] = await resolveLoopLineage(outer, depsFor([root, outer]))

        expect(level.count).toBeUndefined()
    })

    it("keeps what it resolved when a parent cannot be read", async () => {
        const levels = await resolveLoopLineage(inner, depsFor([inner]))

        expect(levels.map((level) => level.taskId)).toEqual(["per_quarter"])
    })

    it("stops at the maximum depth", async () => {
        const chain = Array.from({length: 5}, (_, index) =>
            execution(`e${index}`, `e${index + 1}`, {taskId: `loop_${index}`, taskRunId: `tr${index}`, index: 0, value: "v"}))

        const levels = await resolveLoopLineage(chain[0], depsFor(chain), 3)

        expect(levels.map((level) => level.taskId)).toEqual(["loop_2", "loop_1", "loop_0"])
    })
})

describe("executionLineage", () => {
    const levels: LoopLineageLevel[] = [
        {executionId: "1mubyrnLabcdef", parentExecutionId: "root-execution", taskId: "per_region", number: 1, count: 3, value: "EMEA"},
        {executionId: "5j2D9NuQvgj0wiyuADhgmt", parentExecutionId: "1mubyrnLabcdef", taskId: "per_quarter", number: 2, count: 4, value: "Q2"},
    ]

    it("returns nothing for a top-level execution", () => {
        expect(executionLineage([], context)).toBeUndefined()
    })

    it("lists each ancestor loop task and run in order, ending on the current loop task", () => {
        const lineage = executionLineage(levels, context)!

        expect(lineage.crumbs.map((crumb) => crumb.label)).toEqual(["per_region", "1mubyrnL [1/3 - EMEA]", "per_quarter"])
        expect(lineage.title).toBe("5j2D9NuQvgj0wiyuADhgmt [2/4 - Q2]")
    })

    it("links an ancestor run to its execution and a loop task to its sibling iterations", () => {
        const [region, regionRun, quarter] = executionLineage(levels, context)!.crumbs

        expect(regionRun.link).toEqual({name: "executions/update", params: {namespace: "company.team", flowId: "nested_loop_demo", id: "1mubyrnLabcdef"}})
        expect(region.link).toMatchObject({name: "executions/list", query: {"filters[parentId][EQUALS]": "root-execution", "filters[taskId][EQUALS]": "per_region"}})
        expect(quarter.link).toMatchObject({query: {"filters[parentId][EQUALS]": "1mubyrnLabcdef", "filters[taskId][EQUALS]": "per_quarter"}})
    })

    it("drops the count, then the value, when they are unknown or blank", () => {
        const label = (level: Partial<LoopLineageLevel>) => executionLineage([{...levels[0], ...level}], context)!.title

        expect(label({count: undefined})).toBe("1mubyrnLabcdef [1 - EMEA]")
        expect(label({value: "  "})).toBe("1mubyrnLabcdef [1/3]")
        expect(label({count: undefined, value: undefined})).toBe("1mubyrnLabcdef [1]")
    })

    it("truncates a long value and keeps it whole in the tooltip", () => {
        const value = "a-very-long-iteration-value-that-keeps-going"
        const lineage = executionLineage([{...levels[0], value}, levels[1]], context)!
        const runCrumb = lineage.crumbs[1]

        expect(runCrumb.label).toContain("…")
        expect(runCrumb.label).not.toContain(value)
        expect(runCrumb.tooltip).toBe(value)
    })
})

describe("iterationsListLineage", () => {
    it("ends a top-level parent with its short id and the loop task", () => {
        const crumbs = iterationsListLineage("root-execution", [], "per_region", context)

        expect(crumbs.map((crumb) => crumb.label)).toEqual(["root-exe", "per_region"])
    })

    it("stops at the parent run when no loop task is filtered", () => {
        expect(iterationsListLineage("root-execution", [], undefined, context).map((crumb) => crumb.label)).toEqual(["root-exe"])
    })

    it("names a parent that is itself an iteration with its lineage", () => {
        const levels: LoopLineageLevel[] = [
            {executionId: "1mubyrnLabcdef", parentExecutionId: "root-execution", taskId: "per_region", number: 1, count: 3, value: "EMEA"},
        ]

        expect(iterationsListLineage("1mubyrnLabcdef", levels, "per_quarter", context).map((crumb) => crumb.label))
            .toEqual(["per_region", "1mubyrnL [1/3 - EMEA]", "per_quarter"])
    })
})

describe("iterationsListFilters", () => {
    it("reads the parent and the loop task from the iterations query", () => {
        expect(iterationsListFilters({"filters[parentId][EQUALS]": "p1", "filters[kind][EQUALS]": "LOOP", "filters[taskId][EQUALS]": "t1"}))
            .toEqual({parentId: "p1", taskId: "t1"})
    })

    it("ignores a list that is not filtered on loop iterations of a parent", () => {
        expect(iterationsListFilters({"filters[parentId][EQUALS]": "p1"})).toBeUndefined()
        expect(iterationsListFilters({"filters[kind][EQUALS]": "LOOP"})).toBeUndefined()
    })
})
