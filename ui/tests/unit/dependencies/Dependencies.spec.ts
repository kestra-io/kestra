import {describe, it, expect, vi} from "vitest"
import {defineComponent, h, nextTick} from "vue"
import {createI18n} from "vue-i18n"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

// Shared across calls so the navigation test can assert on it; a fresh spy per useRouter()
// call would be unreachable from the test body.
const routerPush = vi.hoisted(() => vi.fn())

vi.mock("vue-router", () => ({
    useRoute: () => ({
        name: "flows/update/dependencies",
        params: {namespace: "qa.topology", id: "deploy_dashboard"},
        query: {},
    }),
    useRouter: () => ({push: routerPush}),
}))

// `openedNodeID` is the ref the last mount received, so a test can simulate a canvas
// double-click without driving the canvas itself.
const deps = vi.hoisted(() => ({
    openedNodeID: null as unknown as {value: string | undefined},
}))

vi.mock("../../../src/components/dependencies/composables/useDependencies", async () => {
    const {ref} = await import("vue")
    return {
        useDependencies: () => {
            const openedNodeID = ref<string | undefined>(undefined)
            deps.openedNodeID = openedNodeID
            return {
                getElements: () => [
                    {data: {id: "n1", type: "NODE", flow: "my-flow", namespace: "qa.topology", metadata: {subtype: "FLOW"}}},
                ],
                chartNodes: ref([]),
                chartEdges: ref([]),
                shownNodeIDs: ref(null),
                isolateGroup: vi.fn(),
                toggleGroup: vi.fn(),
                clearGroup: vi.fn(),
                activeGroup: ref(undefined),
                isLoading: ref(false),
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
import en from "../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

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
        options: {type: Object, default: undefined},
    },
    setup: () => () => h("div", {class: "ks-graph-stub"}),
})

function mountGraphView(dagView: boolean) {
    routerPush.mockClear()

    return mount(Dependencies, {
        props: {dagView},
        global: {
            plugins: [i18n, KestraDesignSystem],
            stubs: {
                KsGraph: KsGraphStub,
                KsSplitter: passThroughStub("KsSplitter"),
                KsSplitterPanel: passThroughStub("KsSplitterPanel"),
                Table: true,
                GroupPicker: true,
                NodeDetails: true,
                DagCanvas: true,
            },
        },
    })
}

// The flow, execution and namespace views regressed repeatedly by inheriting the asset
// view's behaviour; these pin the dagView gate in both directions.
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
