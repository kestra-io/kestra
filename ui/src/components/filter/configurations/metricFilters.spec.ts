import {beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia} from "pinia"
import {Comparators} from "@kestra-io/design-system"
import {routedPinia} from "../../../../tests/unit/memoryRouter"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import {useFlowStore} from "../../../stores/flow"
import {useFlowMetricFilter, useMetricFilter} from "./metricFilters"

vi.mock("vue-i18n", async (importOriginal) => ({
    ...await importOriginal<typeof import("vue-i18n")>(),
    useI18n: () => ({t: (key: string) => key}),
}))

const execution: Execution = {
    id: "execution",
    namespace: "tests",
    flowId: "metrics",
    flowRevision: 1,
    state: {current: "SUCCESS", histories: []},
}

beforeEach(() => {
    setActivePinia(routedPinia())
})

describe("metric filter configurations", () => {
    it.each([
        {useFilter: useMetricFilter, title: "metric_filters", keys: ["metric"]},
        {useFilter: useFlowMetricFilter, title: "flow_metric_filters", keys: ["task", "metric", "timeRange"]},
    ])("declares the title, search placeholder and keys for $title", ({useFilter, title, keys}) => {
        const config = useFilter().value

        expect(config.title).toBe(`filter.titles.${title}`)
        expect(config.searchPlaceholder).toBe("filter.search_placeholders.search_metrics")
        expect(config.keys.map(({key}) => key)).toEqual(keys)
        for (const key of config.keys) {
            expect(key.valueType).toBe("select")
            expect(key.comparators).toEqual([Comparators.EQUALS])
        }
    })

    it("maps task runs to run IDs and labels repeated tasks with their loop values", async () => {
        useExecutionsStore().execution = {
            ...execution,
            taskRunList: [
                {id: "run-1", taskId: "extract"},
                {id: "run-2", taskId: "process", value: "first"},
                {id: "run-3", taskId: "process", value: "second"},
                {id: "run-4", taskId: "finish", value: ""},
            ].map(taskRun => ({...taskRun, state: execution.state})),
        }

        const metric = useMetricFilter().value.keys[0]

        expect(await metric.valueProvider!()).toEqual([
            {label: "extract", value: "run-1"},
            {label: "process - first", value: "run-2"},
            {label: "process - second", value: "run-3"},
            {label: "finish", value: "run-4"},
        ])
    })

    it.each([
        {name: "no execution", execution: undefined},
        {name: "no task-run list", execution: {...execution}},
        {name: "an empty task-run list", execution: {...execution, taskRunList: []}},
    ])("returns no execution metric options with $name", async ({execution: current}) => {
        useExecutionsStore().execution = current

        expect(await useMetricFilter().value.keys[0].valueProvider!()).toEqual([])
    })

    it("reads flow task and metric options from their respective store lists", async () => {
        const store = useFlowStore()
        store.tasksWithMetrics = ["extract", "process"]
        store.metrics = ["duration", "count"]
        const [task, metric] = useFlowMetricFilter().value.keys

        expect(await task.valueProvider!()).toEqual([
            {label: "extract", value: "extract"},
            {label: "process", value: "process"},
        ])
        expect(await metric.valueProvider!()).toEqual([
            {label: "duration", value: "duration"},
            {label: "count", value: "count"},
        ])
    })

    it.each([
        {useFilter: useMetricFilter, labels: {metric: "metric"}},
        {useFilter: useFlowMetricFilter, labels: {task: "task", metric: "metric", timeRange: "timeRange_metric"}},
    ])("labels every key with its own filter translations", ({useFilter, labels}) => {
        const keys = useFilter().value.keys

        expect(keys.map(({key, label, description}) => [key, label, description])).toEqual(
            Object.entries(labels).map(([key, name]) => [key, `filter.${name}.label`, `filter.${name}.description`]),
        )
    })

    it("offers the relative date ranges for the flow time range key and does not group it", async () => {
        const timeRange = useFlowMetricFilter().value.keys[2]

        expect(timeRange.groupable).toBe(false)
        expect(await timeRange.valueProvider!()).toEqual([
            {label: "datepicker.last5minutes", value: "PT5M"},
            {label: "datepicker.last15minutes", value: "PT15M"},
            {label: "datepicker.last1hour", value: "PT1H"},
            {label: "datepicker.last12hours", value: "PT12H"},
            {label: "datepicker.last24hours", value: "PT24H"},
            {label: "datepicker.last48hours", value: "PT48H"},
            {label: "datepicker.last7days", value: "PT168H"},
            {label: "datepicker.last30days", value: "PT720H"},
            {label: "datepicker.last365days", value: "PT8760H"},
        ])
    })

    it("keeps execution and flow filter keys independent", () => {
        const executionConfig = useMetricFilter().value
        const flow = useFlowMetricFilter().value
        executionConfig.keys[0].label = "execution-only label"
        executionConfig.keys.push({...executionConfig.keys[0], key: "extra"})

        expect(flow.keys.map(({key}) => key)).toEqual(["task", "metric", "timeRange"])
        expect(flow.keys[1].label).toBe("filter.metric.label")
    })
})
