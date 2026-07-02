import {describe, it, expect, vi, beforeEach} from "vitest"
import {ref, nextTick} from "vue"
import {useDependencies, transformResponse} from "../../../../src/components/dependencies/composables/useDependencies"
import {type Node, type Edge, FLOW, EXECUTION, NAMESPACE} from "../../../../src/components/dependencies/utils/types"
import {setActivePinia, createPinia} from "pinia"
import {mount} from "@vue/test-utils"
import {useNamespacesStore} from "override/stores/namespaces"
import {AxiosResponse} from "axios"
import {useFlowStore} from "../../../../src/stores/flow"
import {RouteParams} from "vue-router"
import {getDependencies} from "../../../fixtures/dependencies/getDependencies"

// ─── CSS var sentinels ────────────────────────────────────────────────────────
// Set recognisable values so we can assert which colour path each node took,
// since getComputedStyle always returns "" for custom properties in jsdom.
const CSS_VARS: Record<string, string> = {
    "--ks-dependencies-node-background-default":  "default-bg",
    "--ks-dependencies-node-border-default":      "default-border",
    "--ks-dependencies-node-background-hovered":  "hovered-bg",
    "--ks-dependencies-node-border-hovered":      "hovered-border",
    "--ks-dependencies-node-background-selected": "selected-bg",
    "--ks-dependencies-node-border-selected":     "selected-border",
    "--ks-dependencies-node-background-faded":    "faded-bg",
    "--ks-dependencies-node-border-faded":        "faded-border",
    "--ks-dependencies-node-background-assets":   "assets-bg",
    "--ks-dependencies-node-border-assets":       "assets-border",
    "--ks-dependencies-edge-default":             "default-edge",
    "--ks-dependencies-edge-hovered":             "hovered-edge",
    "--ks-dependencies-edge-selected":            "selected-edge",
    "--ks-dependencies-edge-faded":               "faded-edge",
}

function setCSSVars() {
    Object.entries(CSS_VARS).forEach(([k, v]) =>
        document.documentElement.style.setProperty(k, v),
    )
}

// ─── Controlled graph fixture ─────────────────────────────────────────────────
// A --e1--> B --e2--> C    D (isolated, no edges)
// A is the pre-selected initial node.
function makeControlledElements() {
    return [
        {data: {id: "A", type: "NODE", flow: "flow-a", namespace: "ns", metadata: {subtype: "FLOW"}}},
        {data: {id: "B", type: "NODE", flow: "flow-b", namespace: "ns", metadata: {subtype: "FLOW"}}},
        {data: {id: "C", type: "NODE", flow: "flow-c", namespace: "ns", metadata: {subtype: "FLOW"}}},
        {data: {id: "D", type: "NODE", flow: "flow-d", namespace: "ns", metadata: {subtype: "FLOW"}}},
        {data: {id: "e1", type: "EDGE", source: "A", target: "B"}},
        {data: {id: "e2", type: "EDGE", source: "B", target: "C"}},
    ]
}

function mountControlled(initialNodeID = "A") {
    const graphRef = makeGraphRef()
    const fetchAssetDependencies = vi.fn().mockResolvedValue({
        data:  makeControlledElements(),
        count: 4,
    })
    const wrapper = mount({
        template: "<div></div>",
        setup() {
            const composable = useDependencies(
                graphRef, FLOW, initialNodeID, {}, fetchAssetDependencies,
            )
            return {composable}
        },
    })
    return {wrapper, graphRef, ...(wrapper.vm.composable as ReturnType<typeof useDependencies>)}
}

vi.mock("vue-router", () => ({
  useRouter: () => ({push: vi.fn(), replace: vi.fn(), currentRoute: {value: {path: "/"}}, beforeEach: vi.fn(), afterEach: vi.fn()}),
  useRoute: () => ({params: {}, query: {}, path: "/"}),
  routerKey: Symbol("router"),
}))

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

// Minimal KsGraph ref mock that satisfies the KsGraphRef interface.
function makeGraphRef() {
  return ref({
    zoomIn:             vi.fn(),
    zoomOut:            vi.fn(),
    fit:                vi.fn(),
    exportAsImage:      vi.fn(),
    getEchartsInstance: vi.fn(),
  })
}

// ECharts instance mock that exposes known node positions via getData().
function makeChartMock(nodePositions: Record<string, {x: number; y: number}>, W = 600, H = 400) {
    const ids = Object.keys(nodePositions)
    const layouts = Object.values(nodePositions)
    const dataMock = {
        count:         vi.fn(() => ids.length),
        getId:         vi.fn((i: number) => ids[i]),
        getName:       vi.fn((i: number) => ids[i]),
        getItemLayout: vi.fn((i: number) => layouts[i]),
    }
    return {
        setOption:      vi.fn(),
        dispatchAction: vi.fn(),
        getWidth:       vi.fn(() => W),
        getHeight:      vi.fn(() => H),
        // Immediately invoke "finished" so capturePositions runs synchronously in tests
        on:             vi.fn((event: string, handler: () => void) => { if (event === "finished") handler() }),
        off:            vi.fn(),
        getModel:   vi.fn(() => ({
            getSeriesByIndex: vi.fn(() => ({
                getData:          vi.fn(() => dataMock),
                coordinateSystem: null,
            })),
        })),
    }
}

const mountComponentWithUseDependencies = (
    subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE = FLOW,
    initialNodeID: string = "test-id",
    params: RouteParams = {},
    isTesting: boolean = true,
  ) => {
    const graphRef = makeGraphRef()
    const wrapper = mount({
      template: "<div></div>",
      setup() {
        const composable = useDependencies(graphRef, subtype, initialNodeID, params, isTesting ? async () => {
            return {
                data: getDependencies({
                    subtype,
                }),
                count: 42,
            }
        } : undefined)
        return {composable}
      },
    })
    const composable = wrapper.vm.composable as ReturnType<typeof useDependencies>
    return {wrapper, graphRef, ...composable}
  }

describe("useDependencies composable", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe("onMounted", () => {
    it("should load elements in testing mode", async () => {
      const {isLoading, getElements} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true)

      await nextTick()

      expect(isLoading.value).toBe(false)
      expect(getElements().length).toBeGreaterThan(0)
    })

    it("should load elements from namespace store for subtype NAMESPACE", async () => {
      const nameSpacesStore = useNamespacesStore()
      const mockData = {
        nodes: [{uid: "n1", id: "f1", namespace: "ns"}],
        edges: [{source: "n1", target: "n2"}],
      }

      vi.spyOn(nameSpacesStore, "loadDependencies").mockResolvedValue({
        data: mockData,
      } as AxiosResponse)

      const {isLoading, getElements} = mountComponentWithUseDependencies("NAMESPACE", "test-id", {}, false)

      await nextTick()

      expect(isLoading.value).toBe(false)
      expect(getElements().length).toBeGreaterThan(0)
    })

    it("should load elements from flow store for subtype FLOW", async () => {
      const flowStore = useFlowStore()
      const mockData = {
        nodes: [{uid: "n1", id: "f1", namespace: "ns"}],
        edges: [{source: "n1", target: "n2"}],
      }

      vi.spyOn(flowStore, "loadDependencies").mockResolvedValue({
        data: transformResponse(mockData, FLOW),
        count: 2,
      })

      const {isLoading, getElements} = mountComponentWithUseDependencies("FLOW", "test-id", {}, false)

      await nextTick()

      expect(isLoading.value).toBe(false)
      expect(getElements().length).toBeGreaterThan(0)
    })
  })

  describe("node selection", () => {
    it("should select an existing node and update selectedNodeID", async () => {
      const {selectNode, selectedNodeID, getElements} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true)
      await nextTick()

      // Pick a real node from the generated fixture data.
      const firstNode = getElements().find((el) => el.data.type === "NODE")
      expect(firstNode).toBeDefined()

      selectNode(firstNode!.data.id as string)
      expect(selectedNodeID.value).toBe(firstNode!.data.id)
    })

    it("should not change selection when node does not exist", async () => {
      const {selectNode, selectedNodeID} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true)
      await nextTick()

      // Testing mode auto-selects the first node; capture that initial selection.
      const initialSelection = selectedNodeID.value

      selectNode("non-existent-id")

      // Selection must remain unchanged.
      expect(selectedNodeID.value).toBe(initialSelection)
    })
  })

  const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

  describe("SSE", () => {
    it("should close SSE on unmount when subtype is EXECUTION", async () => {
      const close = vi.fn()
      let instantiated = false
      vi.stubGlobal("EventSource", class AnonEventSource {
        close: () => void = close
        constructor() {
          instantiated = true
        }
      })

      const {wrapper} = await mountComponentWithUseDependencies(EXECUTION)

      // when mount is finished, MockEventSource should have been instantiated to listen to execution updates
      for(let i = 0; i < 5 && !instantiated; i++) {
        await wait(100)
      }

      wrapper.unmount()

      expect(close).toHaveBeenCalled()
    })
  })

  // ── node hover ────────────────────────────────────────────────────────────────
  // Hover is handled entirely by ECharts' built-in emphasis.focus="adjacency".
  // We verify that every node carries:
  //   • emphasis.itemStyle  → hover colour (applied by ECharts on mouseover)
  //   • blur.itemStyle      → same as base itemStyle (so selection colours survive)
  describe("node hover", () => {
    beforeEach(() => setCSSVars())

    it("every node carries emphasis.itemStyle with the hover colour", async () => {
      const {graphNodes} = mountControlled("A")
      await nextTick()

      graphNodes.value.forEach(n => {
        expect((n.emphasis as any)?.itemStyle?.color).toBe("hovered-bg")
        expect((n.emphasis as any)?.itemStyle?.borderColor).toBe("hovered-border")
      })
    })

    it("every edge carries emphasis.lineStyle with the hover edge colour", async () => {
      const {graphEdges} = mountControlled("A")
      await nextTick()

      graphEdges.value.forEach(e => {
        expect((e.emphasis as any)?.lineStyle?.color).toBe("hovered-edge")
      })
    })

    it("blur.itemStyle matches base itemStyle so selection colours survive hover", async () => {
      const {graphNodes, selectNode} = mountControlled("A")
      await nextTick()

      // Select node A — it gets selected-bg.
      selectNode("A")
      await nextTick()

      const nodeA = graphNodes.value.find(n => n.id === "A")!
      expect(nodeA.itemStyle?.color).toBe("selected-bg")
      // blur must mirror base so ECharts doesn't override the selection colour
      // when another node is hovered.
      expect((nodeA.blur as any)?.itemStyle?.color).toBe("selected-bg")
    })

    it("unselected nodes have blur.itemStyle matching their default base colour", async () => {
      const {graphNodes} = mountControlled("nonexistent")
      await nextTick()

      graphNodes.value.forEach(n => {
        expect((n.blur as any)?.itemStyle?.color).toBe(n.itemStyle?.color)
        expect((n.blur as any)?.itemStyle?.opacity ?? 1).toBe(n.itemStyle?.opacity ?? 1)
      })
    })
  })

  describe("focusNode", () => {
    // Known layout:
    //   A(100,200)  B(300,400)  C(200,100)
    //   canvas W=600 H=400
    //
    // ECharts graph `center` is in data coordinates.
    // focusNode sets center=[pos.x, pos.y] to place the node at canvas centre.
    const NODE_POSITIONS = {A: {x: 100, y: 200}, B: {x: 300, y: 400}, C: {x: 200, y: 100}}
    const CANVAS_W = 600
    const CANVAS_H = 400

    async function mountWithChart() {
        const chartMock = makeChartMock(NODE_POSITIONS, CANVAS_W, CANVAS_H)
        const graphRef = ref({
            fit:                vi.fn(),
            zoomIn:             vi.fn(),
            zoomOut:            vi.fn(),
            exportAsImage:      vi.fn(),
            getEchartsInstance: vi.fn(() => chartMock),
            $el:                document.createElement("div"),
        })
        const fetchAssetDependencies = vi.fn().mockResolvedValue({
            data: [
                {data: {id: "A", type: "NODE", flow: "fa", namespace: "ns", metadata: {subtype: "FLOW"}}},
                {data: {id: "B", type: "NODE", flow: "fb", namespace: "ns", metadata: {subtype: "FLOW"}}},
                {data: {id: "C", type: "NODE", flow: "fc", namespace: "ns", metadata: {subtype: "FLOW"}}},
            ],
            count: 3,
        })
        vi.useFakeTimers()
        const wrapper = mount({
            template: "<div></div>",
            setup() {
                // Use initialNodeID="X" (nonexistent) so no node is pre-selected,
                // leaving selectedNodeID undefined and letting tests control selection.
                const composable = useDependencies(graphRef as any, FLOW, "X", {}, fetchAssetDependencies)
                return {composable}
            },
        })
        // Flush microtasks (promise resolution + Vue reactivity) so onMounted's
        // async fetch resolves, then advance fake timers past jsdom's rAF polyfill
        // (setTimeout 16 ms) so capturePositions() runs and storedPositions is populated.
        await nextTick()
        await nextTick()
        await vi.runAllTimersAsync()
        await nextTick()
        vi.useRealTimers()
        chartMock.setOption.mockClear()
        const {selectNode} = wrapper.vm.composable as ReturnType<typeof useDependencies>
        return {selectNode, chartMock}
    }

    it("calls setOption with zoom=1.8 and correct center when node A is selected", async () => {
        // center = [pos.x, pos.y] — ECharts graph center is in data coordinates
        const {selectNode, chartMock} = await mountWithChart()

        selectNode("A")
        await nextTick()
        await nextTick()

        expect(chartMock.setOption).toHaveBeenCalledWith(
            expect.objectContaining({
                series: [expect.objectContaining({zoom: 1.8, center: [100, 200]})],
            }),
            false,
        )
    })

    it("centers on the correct node when selection changes to B", async () => {
        // center = [pos.x, pos.y] — ECharts graph center is in data coordinates
        const {selectNode, chartMock} = await mountWithChart()

        selectNode("B")
        await nextTick()
        await nextTick()

        expect(chartMock.setOption).toHaveBeenCalledWith(
            expect.objectContaining({
                series: [expect.objectContaining({zoom: 1.8, center: [300, 400]})],
            }),
            false,
        )
    })

    it("does not call setOption for centering when node has no stored position", async () => {
        const {selectNode, chartMock} = await mountWithChart()

        selectNode("nonexistent")
        await nextTick()
        await nextTick()

        const focusCalls = chartMock.setOption.mock.calls.filter(
            (args: any[]) => args[0]?.series?.[0]?.zoom !== undefined,
        )
        expect(focusCalls).toHaveLength(0)
    })
  })

  describe("handlers", () => {
    it("should reset selection when clearSelection is invoked", async () => {
      const {handlers, selectNode, selectedNodeID, getElements} = mountComponentWithUseDependencies()
      await nextTick()

      const firstNode = getElements().find((el) => el.data.type === "NODE")
      if (firstNode) selectNode(firstNode.data.id as string)

      handlers.clearSelection()

      expect(selectedNodeID.value).toBeUndefined()
    })

    it("should call graphRef.fit when fit handler is invoked", async () => {
      const {handlers, graphRef} = mountComponentWithUseDependencies()
      await nextTick()

      handlers.fit()

      expect(graphRef.value.fit).toHaveBeenCalled()
    })

    it("should dim nodes not in highlightShown list", async () => {
      const {handlers, graphNodes, getElements} = mountComponentWithUseDependencies()
      await nextTick()

      const allNodes = getElements().filter((el) => el.data.type === "NODE")
      if (allNodes.length < 2) return // skip if not enough nodes

      const firstId = allNodes[0].data.id as string
      handlers.highlightShown([firstId])

      // All nodes except the highlighted one should have reduced opacity.
      const dimmedNode = graphNodes.value.find((n) => n.id !== firstId)
      expect(dimmedNode?.itemStyle?.opacity).toBeLessThan(1)
    })
  })
})

it("should transform API response to dependency elements", () => {
  const response = {
    nodes: [{uid: "n1", id: "f1", namespace: "ns"}],
    edges: [{source: "n1", target: "n2"}],
  }

  const elements = transformResponse(response, FLOW)
  const node = elements[0].data as Node
  const edge = elements[1].data as Edge

  expect(elements).toHaveLength(2)
  expect(node.id).toBe("n1")
  expect(edge.source).toBe("n1")
  expect(edge.target).toBe("n2")
})
