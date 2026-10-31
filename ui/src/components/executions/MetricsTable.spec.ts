import {defineComponent, h, nextTick, onMounted, type PropType} from "vue"
import {beforeEach, describe, expect, it, vi} from "vitest"
import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"

import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"
import MetricsTable from "./MetricsTable.vue"

vi.mock("@kestra-io/design-system", async () => {
    const {ref} = await import("vue")

    return {
        default: {install: () => {}},
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
})
