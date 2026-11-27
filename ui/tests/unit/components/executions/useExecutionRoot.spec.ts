import {afterEach, describe, expect, it, vi} from "vitest";
import {defineComponent} from "vue";
import {flushPromises, mount} from "@vue/test-utils";

const replace = vi.fn();

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "company.team", flowId: "a-flow", id: "an-id"}}),
    useRouter: () => ({replace}),
}));
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}));
vi.mock("../../../../src/stores/flow", () => ({useFlowStore: () => ({loadDependencies: async () => ({count: 0})})}));
vi.mock("../../../../src/stores/executions", () => ({useExecutionsStore: () => ({followExecution: () => {}, closeSSE: () => {}})}));
for (const path of [
    "../../../../src/components/executions/Logs.vue",
    "../../../../src/components/executions/Gantt.vue",
    "../../../../src/components/executions/Topology.vue",
    "../../../../src/components/executions/overview/Overview.vue",
    "../../../../src/components/demo/AuditLogs.vue",
    "../../../../src/components/demo/Assets.vue",
    "../../../../src/components/executions/ExecutionMetric.vue",
    "../../../../src/components/executions/outputs/Wrapper.vue",
    "../../../../src/components/dependencies/Dependencies.vue",
]) {
    vi.doMock(path, () => ({default: {}}));
}

const openedTab = async () => {
    const {useExecutionRoot} = await import("../../../../src/components/executions/composables/useExecutionRoot");
    mount(defineComponent({
        setup() {
            useExecutionRoot().setupLifecycle();
            return () => null;
        },
    }));
    await flushPromises();
    return replace.mock.calls[0]?.[0]?.params?.tab;
};

// kestra-io/kestra-ee#11443: Settings shows Gantt when nothing is stored, so an execution must open there too.
describe("the execution page default tab", () => {
    afterEach(() => {
        localStorage.clear();
        replace.mockClear();
    });

    it("opens on Gantt when no default is stored", async () => {
        expect(await openedTab()).toBe("gantt");
    });

    it("opens on the stored default", async () => {
        localStorage.setItem("executeDefaultTab", "logs");

        expect(await openedTab()).toBe("logs");
    });
});
