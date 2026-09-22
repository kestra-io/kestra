import {beforeEach, describe, expect, it, vi} from "vitest";
import {createPinia, setActivePinia} from "pinia";
import {ElMessageBox} from "element-plus";

const axiosPost = vi.fn();
const axiosPut = vi.fn();
const axiosGet = vi.fn();

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}));

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({
        push: vi.fn(),
        replace: vi.fn(),
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
    }),
}));

vi.mock("../../../src/utils/axios", () => ({
    useAxios: () => ({
        get: axiosGet,
        post: axiosPost,
        put: axiosPut,
        patch: vi.fn(),
        delete: vi.fn(),
    }),
}));

vi.mock("element-plus", async (importOriginal) => {
    const actual = await importOriginal<typeof import("element-plus")>();
    const ElNotification = Object.assign(vi.fn(), {closeAll: vi.fn()});
    return {...actual, ElMessageBox: vi.fn(), ElNotification};
});

const flowYaml = (triggerId: string) => [
    "id: my-flow",
    "namespace: my.ns",
    "tasks:",
    "  - id: t1",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello",
    "triggers:",
    `  - id: ${triggerId}`,
    "    type: io.kestra.plugin.core.trigger.Schedule",
    "    cron: 0 9 * * *",
].join("\n");

const SAVED_YAML = flowYaml("daily");
const RENAMED_YAML = flowYaml("daily-renamed");

async function setupStore(editedYaml: string) {
    const {useFlowStore} = await import("../../../src/stores/flow");
    const store = useFlowStore();

    store.flow = {id: "my-flow", namespace: "my.ns", revision: 1, source: SAVED_YAML} as any;
    store.flowYamlOrigin = SAVED_YAML;
    store.flowYaml = editedYaml;
    store.isCreating = false;

    return store;
}

function mockTriggerRows(rows: {triggerId: string, disabled: boolean}[]) {
    axiosGet.mockImplementation((url: string) =>
        url.includes("/triggers/")
            ? Promise.resolve({data: {results: rows, total: rows.length}})
            : Promise.resolve({status: 200, data: {}}));
}

describe("flow store disabled trigger removal confirmation", () => {
    beforeEach(() => {
        vi.resetModules();
        vi.mocked(ElMessageBox).mockReset();
        axiosPost.mockReset();
        axiosPut.mockReset();
        axiosGet.mockReset();

        axiosPost.mockResolvedValue({data: [{}]});
        axiosPut.mockResolvedValue({
            status: 200,
            data: {id: "my-flow", namespace: "my.ns", revision: 2},
        });
        mockTriggerRows([{triggerId: "daily", disabled: true}]);

        setActivePinia(createPinia());
        localStorage.clear();
    });

    it("prompts and aborts on cancel when a disabled trigger id leaves the flow", async () => {
        vi.mocked(ElMessageBox).mockRejectedValue(new Error("cancel"));

        const store = await setupStore(RENAMED_YAML);
        const outcome = await store.saveAll();

        expect(ElMessageBox).toHaveBeenCalledTimes(1);
        expect(axiosPut).not.toHaveBeenCalled();
        expect(outcome).toBe("no_op");
    });

    it("saves when the prompt is confirmed", async () => {
        vi.mocked(ElMessageBox).mockResolvedValue("confirm" as any);

        const store = await setupStore(RENAMED_YAML);
        const outcome = await store.saveAll();

        expect(ElMessageBox).toHaveBeenCalledTimes(1);
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(outcome).toBe("saved");
    });

    it("does not prompt when the removed trigger was not disabled", async () => {
        mockTriggerRows([{triggerId: "daily", disabled: false}]);

        const store = await setupStore(RENAMED_YAML);
        const outcome = await store.saveAll();

        expect(ElMessageBox).not.toHaveBeenCalled();
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(outcome).toBe("saved");
    });

    it("does not prompt when the disabled trigger keeps its id", async () => {
        const editedYaml = SAVED_YAML.replace("cron: 0 9 * * *", "cron: 0 10 * * *");

        const store = await setupStore(editedYaml);
        const outcome = await store.saveAll();

        expect(ElMessageBox).not.toHaveBeenCalled();
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(outcome).toBe("saved");
    });
});
