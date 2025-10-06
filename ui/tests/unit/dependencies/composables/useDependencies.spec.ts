// tests/useDependencies.spec.ts
import {describe, it, expect, vi, beforeEach} from "vitest";
import {ref, nextTick} from "vue";
import {useDependencies, transformResponse} from "../../../../src/components/dependencies/composables/useDependencies";
import {type Node, type Edge, FLOW, EXECUTION, NAMESPACE} from "../../../../src/components/dependencies/utils/types";
import {setActivePinia, createPinia} from "pinia";
import {mount} from "@vue/test-utils"
import {useNamespacesStore} from "override/stores/namespaces";
import {AxiosResponse} from "axios";
import {useFlowStore} from "../../../../src/stores/flow";
import {RouteParams} from "vue-router";

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}));

const mock = {
  style: vi.fn().mockReturnThis(),
  forEach: vi.fn().mockReturnThis(),
  map: vi.fn().mockReturnThis(),
};

vi.mock("cytoscape", () => {
  return {
    default: vi.fn(() => ({
      nodes: vi.fn(() => mock),
      edges: vi.fn(() => mock),
      // elements: vi.fn(() => ({
      //   removeClass: vi.fn(),
      //   addClass: vi.fn(),
      // })),
      on: vi.fn(),
      ready: vi.fn((cb) => cb()), // immediately call the callback
      // fit: vi.fn(),
      // style: vi.fn(),
      // animate: vi.fn(),
      // getElementById: vi.fn(() => ({ renderPosition: vi.fn() })),
    })),
  }
})

const mountComponentWithUseDependencies = (
    subtype: typeof FLOW | typeof EXECUTION | typeof NAMESPACE = FLOW,
    initialNodeID: string = "test-id",
    params: RouteParams = {},
    isTesting: boolean = true,
    hasRef: boolean = true
  ) => {
    const wrapper = mount({
      template: hasRef ? "<div ref=\"container\"></div>" : "<div></div>",
      setup() {
        const container = ref<HTMLElement | null>(null);
        const composable = useDependencies(container, subtype, initialNodeID, params, isTesting);
        return {container, composable};
      }
    });
    const composable = wrapper.vm.composable as ReturnType<typeof useDependencies>;

    return {wrapper, composable};
  };

describe("useDependencies composable", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  });

  describe("onMounted", () => {
    it("should not load elements when container doesn't have ref", async () => {
      const {composable} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true, false);

      await nextTick();

      expect(composable.isLoading.value).toBe(true);
      expect(composable.getElements().length).toEqual(0);
    });

    it("should load elements in testing mode", async () => {
      const {composable} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true, true)

      await nextTick();

      expect(composable.isLoading.value).toBe(false);
      expect(composable.getElements().length).toBeGreaterThan(0);
    });

    it("should load elements from nameSpace Store for SUBTYPE NAMESPACE", async () => {
      const nameSpacesStore = useNamespacesStore();
      const mockData = {
        nodes: [{uid: "n1", id: "f1", namespace: "ns"}],
        edges: [{source: "n1", target: "n2"}],
      };

      vi.spyOn(nameSpacesStore, "loadDependencies").mockResolvedValue({
        data: mockData,
      } as AxiosResponse);

      const {composable} = mountComponentWithUseDependencies("NAMESPACE", "test-id", {}, false, true);

      await nextTick();

      expect(composable.isLoading.value).toBe(false);
      expect(composable.getElements().length).toBeGreaterThan(0);
    });

    it("should load elements from flow Store for SUBTYPE FLOW", async () => {
      const nameSpacesStore = useNamespacesStore();
      const flowStore = useFlowStore();
      const mockData = {
        nodes: [{uid: "n1", id: "f1", namespace: "ns"}],
        edges: [{source: "n1", target: "n2"}],
      };

      vi.spyOn(nameSpacesStore, "loadDependencies").mockResolvedValue({
        data: mockData,
      } as AxiosResponse);


      vi.spyOn(flowStore, "loadDependencies").mockResolvedValue({
        data: transformResponse(mockData, FLOW),
        count: 2
      });

      const {composable} = mountComponentWithUseDependencies("FLOW", "test-id", {}, false, true);

      await nextTick();

      expect(composable.isLoading.value).toBe(false);
      expect(composable.getElements().length).toBeGreaterThan(0);
    });
  });
});

it("should transform API response to cytoscape elements", () => {
  const response = {
    nodes: [{uid: "n1", id: "f1", namespace: "ns"}],
    edges: [{source: "n1", target: "n2"}],
  };

  const elements = transformResponse(response, FLOW);
  const node = elements[0].data as Node
  const edge = elements[1].data as Edge

  expect(elements).toHaveLength(2);
  expect(node.id).toBe("n1");
  expect(edge.source).toBe("n1");
  expect(edge.target).toBe("n2");
});

//   it("should select a node and update selectedNodeID", async () => {
//     const composable = useDependencies(container, FLOW, "flow1", {id: "flow1", namespace: "ns1"}, true);
//     await nextTick();

//     composable.selectNode("node1");
//     expect(composable.selectedNodeID.value).toBe("node1");
//   });

//   it("should clear selection", async () => {
//     const composable = useDependencies(container, FLOW, "flow1", {id: "flow1", namespace: "ns1"}, true);
//     await nextTick();

//     composable.selectNode("node1");
//     composable.handlers.clearSelection();
//     expect(composable.selectedNodeID.value).toBe(undefined);
//   });

//   it("should open and close SSE when subtype is EXECUTION", async () => {
//     const composable = useDependencies(container, EXECUTION, "exec1", {id: "exec1", namespace: "ns1"}, true);
//     await nextTick();

//     // @ts-ignore
//     composable.openSSE();
//     expect(composable.sse.value).toBeDefined();

//     // @ts-ignore
//     composable.closeSSE();
//     expect(composable.sse.value).toBeUndefined();
//   });
