import {beforeEach, describe, expect, it, vi} from "vitest";
import {createPinia, setActivePinia} from "pinia";

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
    return {...actual, ElNotification};
});

type FlowStore = Awaited<ReturnType<typeof setupStore>>;

async function answerDialog(store: FlowStore, confirmed: boolean) {
    await vi.waitUntil(() => store.removedDisabledTriggers.length > 0);
    store.answerRemovedDisabledTriggers(confirmed);
}

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
        const store = await setupStore(RENAMED_YAML);
        const saving = store.saveAll();
        await answerDialog(store, false);

        expect(await saving).toBe("no_op");
        expect(axiosPut).not.toHaveBeenCalled();
    });

    it("names the disabled triggers it asks about and saves once confirmed", async () => {
        const store = await setupStore(RENAMED_YAML);
        const saving = store.saveAll();

        await vi.waitUntil(() => store.removedDisabledTriggers.length > 0);
        expect(store.removedDisabledTriggers).toEqual(["daily"]);

        store.answerRemovedDisabledTriggers(true);

        expect(await saving).toBe("saved");
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(store.removedDisabledTriggers).toEqual([]);
    });

    it("does not prompt when the removed trigger was not disabled", async () => {
        mockTriggerRows([{triggerId: "daily", disabled: false}]);

        const store = await setupStore(RENAMED_YAML);
        const outcome = await store.saveAll();

        expect(store.removedDisabledTriggers).toEqual([]);
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(outcome).toBe("saved");
    });

    it("does not prompt when the disabled trigger keeps its id", async () => {
        const editedYaml = SAVED_YAML.replace("cron: 0 9 * * *", "cron: 0 10 * * *");

        const store = await setupStore(editedYaml);
        const outcome = await store.saveAll();

        expect(store.removedDisabledTriggers).toEqual([]);
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(outcome).toBe("saved");
    });
});
