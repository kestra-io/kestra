import {describe, it, expect, vi} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import {createI18n} from "vue-i18n"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

vi.mock("vue-router", () => ({
    useRoute: () => ({
        name: "flows/update/dependencies",
        params: {namespace: "qa.topology", id: "deploy_dashboard"},
        query: {},
    }),
}))

// `isLoading` keeps the graph/table branch of the template unmounted: this suite only
// covers the execution chart in the header, which renders independently of it.
vi.mock("../../../src/components/dependencies/composables/useDependencies", () => ({
    useDependencies: () => ({
        getElements: () => [],
        chartNodes: ref([]),
        chartEdges: ref([]),
        isLoading: ref(true),
        isRendering: ref(false),
        selectedNodeID: ref(undefined),
        selectNode: vi.fn(),
        handleNodeClick: vi.fn(),
        handlers: {},
    }),
}))

import Dependencies from "../../../src/components/dependencies/Dependencies.vue"
import en from "../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

type Filter = {field: string; value: string; operation: string};

// Records the `filters` prop as it stands at the moment `refresh()` is invoked, which is
// what distinguishes a post-flush watcher (new duration) from a pre-flush one (stale).
const filtersAtRefresh: Filter[][] = []

const TimeSeriesStub = defineComponent({
    name: "TimeSeries",
    props: {
        chart: {type: Object, default: undefined},
        filters: {type: Array, default: () => []},
    },
    setup(props, {expose}) {
        expose({
            refresh: () => {
                filtersAtRefresh.push(props.filters as Filter[])
            },
        })
        return () => h("div", {class: "time-series-stub"})
    },
})

const ChartDurationSelectStub = defineComponent({
    name: "ChartDurationSelect",
    props: {modelValue: {type: String, default: ""}},
    emits: ["update:modelValue"],
    setup: () => () => h("div", {class: "duration-stub"}),
})

function mountDependencies() {
    filtersAtRefresh.length = 0

    return mount(Dependencies, {
        global: {
            plugins: [i18n, KestraDesignSystem],
            stubs: {
                TimeSeries: TimeSeriesStub,
                ChartDurationSelect: ChartDurationSelectStub,
            },
        },
    })
}

const timeRangeOf = (filters: Filter[]) => filters.find(({field}) => field === "timeRange")?.value

describe("dependencies Dependencies.vue — execution chart", () => {
    it("gives each aggregation a distinct graphStyle, so the chart preview is not rejected as invalid", () => {
        // Given a mounted Dependencies tab
        const wrapper = mountDependencies()

        // When reading the chart definition handed to the chart component
        const columns = wrapper.findComponent(TimeSeriesStub).props("chart")?.data?.columns

        // Then both aggregations carry a graphStyle, and the two differ — the backend
        // defaults a styleless aggregation to LINES and rejects duplicate styles with a 422.
        expect(columns.total.graphStyle).toBe("BARS")
        expect(columns.duration.graphStyle).toBe("LINES")

        const aggregations = Object.values(columns as Record<string, {agg?: string; graphStyle?: string}>)
            .filter((column) => column.agg)
        const graphStyles = new Set(aggregations.map((column) => column.graphStyle))
        expect(graphStyles.size).toBe(aggregations.length)
    })

    it("serializes graphStyle into the chart content that is sent for preview", () => {
        // Given a mounted Dependencies tab
        const wrapper = mountDependencies()

        // When reading the serialized content, which is the payload POSTed to the preview endpoint
        const content = wrapper.findComponent(TimeSeriesStub).props("chart")?.content

        // Then the styles survived serialization
        expect(content).toContain("graphStyle: BARS")
        expect(content).toContain("graphStyle: LINES")
    })

    it("refreshes the chart with the newly selected duration when the duration changes", async () => {
        // Given a mounted Dependencies tab showing the default 14-day window
        const wrapper = mountDependencies()
        expect(timeRangeOf(wrapper.findComponent(TimeSeriesStub).props("filters") as Filter[])).toBe("PT336H")
        expect(filtersAtRefresh).toHaveLength(0)

        // When another duration is selected
        wrapper.findComponent(ChartDurationSelectStub).vm.$emit("update:modelValue", "PT168H")
        await nextTick()

        // Then the chart is refreshed once, and it already sees the new duration — the chart
        // does not watch its filters prop, so without the explicit refresh nothing reloads.
        expect(filtersAtRefresh).toHaveLength(1)
        expect(timeRangeOf(filtersAtRefresh[0])).toBe("PT168H")
    })

    it("does not refresh the chart when the selected duration is unchanged", async () => {
        // Given a mounted Dependencies tab
        const wrapper = mountDependencies()

        // When the selector re-emits the duration already in effect
        wrapper.findComponent(ChartDurationSelectStub).vm.$emit("update:modelValue", "PT336H")
        await nextTick()

        // Then no refetch is triggered
        expect(filtersAtRefresh).toHaveLength(0)
    })
})
