import {describe, it, expect, vi} from "vitest"
import {defineComponent, h, nextTick} from "vue"
import {createI18n} from "vue-i18n"
import {mount, RouterLinkStub} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

// Shared across calls so a future test of openNode()'s navigation can assert on it;
// a fresh spy per useRouter() call would be unreachable from the test body.
const routerPush = vi.hoisted(() => vi.fn())

vi.mock("vue-router", () => ({
    useRoute: () => ({
        name: "flows/update/dependencies",
        params: {namespace: "qa.topology", id: "deploy_dashboard"},
        query: {},
    }),
    // openNode() pushes a route when a node is opened. This suite covers only the
    // execution chart, so navigation is unasserted here, but the component calls
    // useRouter() at setup and would throw without it.
    useRouter: () => ({push: routerPush}),
}))

// Bridge into the mocked composable: `loaded` flips the template from the loading
// branch (all the chart tests need) to the graph/table branch (the gating tests
// need), and `openedNodeID` is the ref the last mount received, so a test can
// simulate a canvas double-click.
const deps = vi.hoisted(() => ({
    loaded: false,
    openedNodeID: null as unknown as {value: string | undefined},
}))

vi.mock("../../../src/components/dependencies/composables/useDependencies", async () => {
    const {ref} = await import("vue")
    return {
        useDependencies: () => {
            const openedNodeID = ref<string | undefined>(undefined)
            deps.openedNodeID = openedNodeID
            return {
                getElements: () => (deps.loaded
                    ? [{data: {id: "n1", type: "NODE", flow: "my-flow", namespace: "qa.topology", metadata: {subtype: "FLOW"}}}]
                    : []),
                chartNodes: ref([]),
                chartEdges: ref([]),
                graphLayout: ref("force"),
                shownNodeIDs: ref(null),
                isolateGroup: vi.fn(),
                toggleGroup: vi.fn(),
                clearGroup: vi.fn(),
                activeGroup: ref(undefined),
                isLoading: ref(!deps.loaded),
                isRendering: ref(false),
                selectedNodeID: ref(undefined),
                selectNode: vi.fn(),
                highlightNode: vi.fn(),
                openedNodeID,
                handleNodeClick: vi.fn(),
                handlers: {highlightShown: vi.fn()},
            }
        },
    }
})

import Dependencies from "../../../src/components/dependencies/Dependencies.vue"
import Table from "../../../src/components/dependencies/components/Table.vue"
import Link from "../../../src/components/dependencies/components/Link.vue"
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
    deps.loaded = false

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

// VTU's default stubs swallow slots, which would hide the whole graph pane.
const passThroughStub = (name: string) => defineComponent({
    name,
    setup: (_, {slots}) => () => h("div", slots.default?.()),
})

// Captures the ECharts option payload without dragging real ECharts into jsdom.
const KsGraphStub = defineComponent({
    name: "KsGraph",
    props: {
        nodes: {type: Array, default: () => []},
        edges: {type: Array, default: () => []},
        loading: {type: Boolean, default: false},
        layout: {type: String, default: "force"},
        options: {type: Object, default: undefined},
    },
    setup: () => () => h("div", {class: "ks-graph-stub"}),
})

/** Mounts the graph/table branch (loaded data) with the given dagView prop. */
function mountGraphView(dagView: boolean) {
    deps.loaded = true
    routerPush.mockClear()

    return mount(Dependencies, {
        props: {dagView},
        global: {
            plugins: [i18n, KestraDesignSystem],
            stubs: {
                TimeSeries: TimeSeriesStub,
                ChartDurationSelect: ChartDurationSelectStub,
                KsGraph: KsGraphStub,
                KsSplitter: passThroughStub("KsSplitter"),
                KsSplitterPanel: passThroughStub("KsSplitterPanel"),
                Table: true,
                NodeDetails: true,
                GroupPicker: true,
                DagCanvas: true,
            },
        },
    })
}

// The flow, execution and namespace views regressed repeatedly by inheriting the
// asset view's behaviour; these pin the dagView gate in both directions.
describe("dependencies Dependencies.vue — asset-view gating", () => {
    it("passes emphasis.focus none to the chart unless the asset view opts in", () => {
        const focusOf = (wrapper: ReturnType<typeof mountGraphView>) =>
            (wrapper.findComponent(KsGraphStub).props("options") as any).series[0].emphasis.focus

        expect(focusOf(mountGraphView(false))).toBe("none")
        expect(focusOf(mountGraphView(true))).toBe("adjacency")
    })

    it("navigates on an opened node only in the asset view", async () => {
        // A canvas double-click surfaces as openedNodeID; the sibling views must ignore it.
        mountGraphView(false)
        deps.openedNodeID.value = "n1"
        await nextTick()
        expect(routerPush).not.toHaveBeenCalled()

        mountGraphView(true)
        deps.openedNodeID.value = "n1"
        await nextTick()
        expect(routerPush).toHaveBeenCalledWith({
            name: "flows/update",
            params: {tenant: undefined, namespace: "qa.topology", id: "my-flow"},
        })
    })
})

describe("dependencies Table.vue — asset-view gating", () => {
    const row = (subtype: string, id: string) =>
        ({data: {id, type: "NODE", flow: subtype === "ASSET" ? "db.schema.customers" : "my-flow", namespace: "ns", metadata: subtype === "EXECUTION" ? {subtype, id: "exec-1", state: "SUCCESS"} : {subtype}}}) as any

    const mountTable = (subtype: string, elements: any[]) => mount(Table, {
        props: {elements, selected: undefined, subtype: subtype as any},
        global: {plugins: [i18n, KestraDesignSystem], stubs: {RouterLink: RouterLinkStub}},
    })

    // One arrow-count per row, in row order: the base guard gives execution rows none.
    const arrowsPerRow = (wrapper: ReturnType<typeof mountTable>) =>
        wrapper.findAll("section#right").map((right) => right.findAllComponents(RouterLinkStub).length)

    it("keeps the Link name and the guarded arrow outside the asset view", async () => {
        const wrapper = mountTable("EXECUTION", [row("FLOW", "f1"), row("EXECUTION", "e1")])
        // Element Plus registers table columns a couple of ticks after mount.
        await nextTick()
        await nextTick()
        await nextTick()

        expect(wrapper.findAllComponents(Link)).toHaveLength(2)
        expect(wrapper.find("code.name").exists()).toBe(false)
        expect(arrowsPerRow(wrapper)).toEqual([1, 0])
    })

    it("keeps the plain code name and the unguarded arrow in the asset view", async () => {
        const wrapper = mountTable("ASSET", [row("ASSET", "a1"), row("FLOW", "f1")])
        await nextTick()
        await nextTick()
        await nextTick()

        expect(wrapper.findAllComponents(Link)).toHaveLength(0)
        expect(wrapper.findAll("code.name").map((code) => code.text())).toEqual(["db.schema.customers", "my-flow"])
        expect(arrowsPerRow(wrapper)).toEqual([1, 1])
    })
})
