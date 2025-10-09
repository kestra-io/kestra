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
import {useMiscStore} from "override/stores/misc";

vi.mock("vue-router", () => ({
  useRouter: () => ({push: vi.fn(), replace: vi.fn(), currentRoute: {value: {path: "/"}}, beforeEach: vi.fn(), afterEach: vi.fn()}),
  useRoute: () => ({params: {}, query: {}, path: "/"}),
}));

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}));

const cyMock = {
  style: vi.fn().mockReturnThis(),
  forEach: vi.fn().mockReturnThis(),
  map: vi.fn().mockReturnThis(),
  fromJson: vi.fn().mockReturnThis(),
  update: vi.fn().mockReturnThis(),
  getElementById: vi.fn().mockReturnThis(),
  nonempty: vi.fn().mockReturnValue(true),
  removeClass: vi.fn().mockReturnThis(),
  addClass: vi.fn().mockReturnThis(),
  connectedEdges: vi.fn().mockReturnThis(),
  connectedNodes: vi.fn().mockReturnThis(),
};

vi.mock("cytoscape", () => {
  return {
    default: vi.fn(() => ({
      nodes: vi.fn(() => cyMock),
      edges: vi.fn(() => cyMock),
      elements: vi.fn(() => cyMock),
      on: vi.fn(),
      ready: vi.fn(),
      fit: vi.fn(),
      style: vi.fn(() => cyMock),
      animate: vi.fn(),
      getElementById: vi.fn(() => cyMock),
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

    return {wrapper, ...composable};
  };

describe("useDependencies composable", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  });

  describe("onMounted", () => {
    it("should not load elements when container doesn't have ref", async () => {
      const {isLoading, getElements} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true, false);

      await nextTick();

      expect(isLoading.value).toBe(false);
      expect(getElements().length).toEqual(0);
    });

    it("should load elements in testing mode", async () => {
      const {isLoading, getElements} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true, true)

      await nextTick();

      expect(isLoading.value).toBe(false);
      expect(getElements().length).toBeGreaterThan(0);
    });

    it("should load elements from namespace store for subtype NAMESPACE", async () => {
      const nameSpacesStore = useNamespacesStore();
      const mockData = {
        nodes: [{uid: "n1", id: "f1", namespace: "ns"}],
        edges: [{source: "n1", target: "n2"}],
      };

      vi.spyOn(nameSpacesStore, "loadDependencies").mockResolvedValue({
        data: mockData,
      } as AxiosResponse);

      const {isLoading, getElements} = mountComponentWithUseDependencies("NAMESPACE", "test-id", {}, false, true);

      await nextTick();

      expect(isLoading.value).toBe(false);
      expect(getElements().length).toBeGreaterThan(0);
    });

    it("should load elements from flow store for subtype FLOW", async () => {
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

      const {isLoading, getElements} = mountComponentWithUseDependencies("FLOW", "test-id", {}, false, true);

      await nextTick();

      expect(isLoading.value).toBe(false);
      expect(getElements().length).toBeGreaterThan(0);
    });
  });

  describe("theme reactivity", () => {
    it("should react to theme changes", async () => {
      mountComponentWithUseDependencies(FLOW, "test-id", {}, true, true); 

      const miscStore = useMiscStore();
      miscStore.theme = "dark";
      await nextTick();

      expect(cyMock.style).toHaveBeenCalled();
      expect(cyMock.fromJson).toHaveBeenCalled();
      expect(cyMock.update).toHaveBeenCalled();

      miscStore.theme = "light";

      await nextTick();

      expect(cyMock.style).toHaveBeenCalled();
      expect(cyMock.fromJson).toHaveBeenCalled();
      expect(cyMock.update).toHaveBeenCalled();
    })
  });

  describe("node selection", () => {
    it("should select a node and update selectedNodeID", () => {
      const {selectNode, selectedNodeID} = mountComponentWithUseDependencies(FLOW, "test-id", {}, true, true);

      selectNode("node1");
      expect(selectedNodeID.value).toBe("node1");
    })
  })

  describe("SSE", () => {
    it("should close SSE on unmount when subtype is EXECUTION", async () => {
      const close = vi.fn();

      vi.stubGlobal("EventSource", vi.fn(() => ({
        close,
      })));

      const {wrapper} = mountComponentWithUseDependencies(EXECUTION);
      await nextTick();

      wrapper.unmount();

      expect(close).toHaveBeenCalled();
    });
  })

  describe("handlers", () => {
    it("should reset selection when clearSelection is invoked", () => {
      const {handlers, selectedNodeID} = mountComponentWithUseDependencies();
      selectedNodeID.value = "some-node";

      handlers.clearSelection();

      expect(selectedNodeID.value).toBeUndefined();
    })
  })
})

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
