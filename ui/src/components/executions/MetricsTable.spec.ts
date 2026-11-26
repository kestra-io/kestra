import {defineComponent, h, nextTick, onMounted, type PropType} from "vue"
import {beforeEach, describe, expect, it, vi} from "vitest"
import type {MetricEntry} from "@kestra-io/kestra-sdk"
import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"

import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"
import MetricsTable from "./MetricsTable.vue"

vi.mock("@kestra-io/design-system", async () => {
    const {ref} = await import("vue")

    return {
        default: {install: () => {}},
        dateUtils: {dateFilter: (value: string) => value},
        useTableColumns: () => ({
            visibleColumns: ref(["taskId", "name", "value", "tags"]),
            updateVisibleColumns: vi.fn(),
        }),
    }
})

vi.mock("./metricDescriptions", () => ({
    useMetricDescriptions: () => () => undefined,
}))

vi.mock("@kestra-io/kestra-sdk/metrics", () => ({
    searchByExecution: vi.fn().mockResolvedValue({results: [], total: 0}),
}))

type LoadData = (query: {page: number; size: number; sort: string}) => Promise<void>

const KsDataTable = defineComponent({
    name: "KsDataTable",
    props: {
        loadData: {
            type: Function as PropType<LoadData>,
            required: true,
        },
    },
    setup(props, {expose, slots}) {
        const reload = () => props.loadData({page: 1, size: 25, sort: "name:asc"})

        expose({
            reload,
            resetAndReload: reload,
        })

        onMounted(reload)

        return () => h("div", slots.default?.())
    },
})

const global = {
    stubs: {
        ChartAreaspline: true,
        Counter: true,
        KsBar: true,
        KsDataTable,
        KsDrawer: true,
        KsIcon: true,
        KsIconButton: true,
        KsTableColumn: true,
        KsTag: true,
        KsTooltip: true,
        Timer: true,
    },
}

describe("MetricsTable", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("reloads metrics when the execution changes", async () => {
        const wrapper = i18nMount(MetricsTable, {
            props: {
                execution: {id: "execution-1"} as Execution,
                showTask: true,
            },
            global,
        })

        await nextTick()

        expect(MetricsAPI.searchByExecution).toHaveBeenLastCalledWith(
            expect.objectContaining({
                executionId: "execution-1",
            }),
        )

        await wrapper.setProps({
            execution: {id: "execution-2"} as Execution,
        })
        await nextTick()

        expect(MetricsAPI.searchByExecution).toHaveBeenLastCalledWith(
            expect.objectContaining({
                executionId: "execution-2",
            }),
        )
        expect(MetricsAPI.searchByExecution).toHaveBeenCalledTimes(2)

        wrapper.unmount()
    })

    it("reloads metrics when the same execution transitions from RUNNING to SUCCESS", async () => {
        const wrapper = i18nMount(MetricsTable, {
            props: {
                execution: {
                    id: "execution-1",
                    state: {current: "RUNNING"},
                } as Execution,
                showTask: true,
            },
            global,
        })

        await nextTick()

        expect(MetricsAPI.searchByExecution).toHaveBeenCalledTimes(1)
        expect(MetricsAPI.searchByExecution).toHaveBeenLastCalledWith(
            expect.objectContaining({
                executionId: "execution-1",
            }),
        )

        await wrapper.setProps({
            execution: {
                id: "execution-1",
                state: {current: "SUCCESS"},
            } as Execution,
        })
        await nextTick()

        expect(wrapper.exists()).toBe(true)
        expect(MetricsAPI.searchByExecution).toHaveBeenCalledTimes(2)
        expect(MetricsAPI.searchByExecution).toHaveBeenLastCalledWith(
            expect.objectContaining({
                executionId: "execution-1",
            }),
        )

        wrapper.unmount()
    })

    it("charts every point of the clicked metric when the execution has more than one page of metrics", async () => {
        const point = (name: string, value: number) => ({
            name,
            taskId: "task-1",
            type: "counter",
            value,
            timestamp: new Date(Date.UTC(2026, 0, 1, 0, 0, value)).toISOString(),
        }) as MetricEntry
        const pages = [
            [...Array.from({length: 999}, (_, index) => point("other", index)), point("rows", 1)],
            [point("rows", 2), point("rows", 3)],
        ]
        vi.mocked(MetricsAPI.searchByExecution).mockImplementation(async ({sort, page}) => {
            if (sort?.[0] !== "timestamp:asc") {
                return {results: [point("rows", 1)], total: 1}
            }
            return {results: pages[(page ?? 1) - 1] ?? [], total: 1002}
        })

        const row = point("rows", 1)
        const wrapper = i18nMount(MetricsTable, {
            props: {
                execution: {id: "execution-1"} as Execution,
                showTask: true,
            },
            global: {
                stubs: {
                    ...global.stubs,
                    KsTableColumn: defineComponent((_, {slots}) => () => h("div", slots.default?.({row}))),
                    KsIconButton: defineComponent(() => () => h("button", {"data-test": "open-chart"})),
                    KsDrawer: defineComponent((_, {slots}) => () => h("div", slots.default?.())),
                    KsBar: defineComponent({
                        name: "KsBar",
                        props: {data: {type: Array, default: null}},
                        setup: () => () => h("div"),
                    }),
                },
            },
        })
        await nextTick()

        await wrapper.find("[data-test='open-chart']").trigger("click")
        await vi.waitFor(() => {
            expect(wrapper.findComponent({name: "KsBar"}).props("data")).toEqual([{name: "rows", data: [1, 2, 3]}])
        })

        wrapper.unmount()
    })
})
