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

// 1.0 store talks to the API through vuex's `$http`, not the newer axios composable.
vi.mock("vuex", () => ({
    useStore: () => ({
        $http: {
            get: axiosGet,
            post: axiosPost,
            put: axiosPut,
            patch: vi.fn(),
            delete: vi.fn(),
        },
    }),
    Store: class {},
}));

vi.mock("element-plus", async (importOriginal) => {
    const actual = await importOriginal<typeof import("element-plus")>();
    const ElNotification = Object.assign(vi.fn(), {closeAll: vi.fn()});
    return {...actual, ElMessageBox: vi.fn(), ElNotification};
});

const FLOW_YAML = [
    "id: my-flow",
    "namespace: my.ns",
    "tasks:",
    "  - id: t1",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello",
].join("\n");

async function setupOutdatedStore() {
    const {useFlowStore} = await import("../../../src/stores/flow");
    const {useEditorStore} = await import("../../../src/stores/editor");

    const store = useFlowStore();
    store.flow = {id: "my-flow", namespace: "my.ns", revision: 1} as any;
    store.flowYaml = FLOW_YAML;
    store.flowYamlOrigin = "";
    store.isCreating = false;
    // Save is only attempted when there is something to save.
    store.haveChange = true;

    // A flow tab must be current so `isFlow` is true and save()/saveAll() take the flow path.
    useEditorStore().current = {name: "Flow", path: "Flow.yaml", flow: true};

    return store;
}

describe("flow store outdated save confirmation", () => {
    beforeEach(() => {
        vi.resetModules();
        vi.mocked(ElMessageBox).mockReset();
        axiosPost.mockReset();
        axiosPut.mockReset();
        axiosGet.mockReset();

        // /flows/validate -> backend flags the in-progress edit as outdated
        axiosPost.mockResolvedValue({data: [{outdated: true}]});
        // /flows/{ns}/{id} (save) -> succeeds
        axiosPut.mockResolvedValue({
            status: 200,
            data: {id: "my-flow", namespace: "my.ns", revision: 2},
        });

        setActivePinia(createPinia());
        localStorage.clear();
    });

    it("prompts before overwriting an outdated revision and aborts on cancel", async () => {
        vi.mocked(ElMessageBox).mockRejectedValue(new Error("cancel"));

        const store = await setupOutdatedStore();
        const outcome = await store.saveAll();

        expect(ElMessageBox).toHaveBeenCalledTimes(1);
        expect(axiosPut).not.toHaveBeenCalled();
        expect(outcome).toBe("no_op");
    });

    it("overwrites the outdated revision when the prompt is confirmed", async () => {
        vi.mocked(ElMessageBox).mockResolvedValue("confirm" as any);

        const store = await setupOutdatedStore();
        const outcome = await store.saveAll();

        expect(ElMessageBox).toHaveBeenCalledTimes(1);
        expect(axiosPut).toHaveBeenCalledTimes(1);
        // saveWithoutRevisionGuard() returns undefined on an existing-flow save.
        expect(outcome).toBeUndefined();
    });

    it("does not prompt when the edited revision is up to date", async () => {
        axiosPost.mockResolvedValue({data: [{}]});

        const store = await setupOutdatedStore();
        const outcome = await store.saveAll();

        expect(ElMessageBox).not.toHaveBeenCalled();
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(outcome).toBeUndefined();
    });

    // save() backs the no-code editor's Ctrl+S (useKeyboardSave) and must gate too
    it("prompts and aborts on cancel when saving an outdated revision via save()", async () => {
        vi.mocked(ElMessageBox).mockRejectedValue(new Error("cancel"));

        const store = await setupOutdatedStore();
        const outcome = await store.save({});

        expect(ElMessageBox).toHaveBeenCalledTimes(1);
        expect(axiosPut).not.toHaveBeenCalled();
        expect(outcome).toBe("no_op");
    });

    it("overwrites the outdated revision via save() when the prompt is confirmed", async () => {
        vi.mocked(ElMessageBox).mockResolvedValue("confirm" as any);

        const store = await setupOutdatedStore();
        const outcome = await store.save({});

        expect(ElMessageBox).toHaveBeenCalledTimes(1);
        expect(axiosPut).toHaveBeenCalledTimes(1);
        expect(outcome).toBeUndefined();
    });

    // Guards the key constant the warning consumers (EditorButtonsWrapper /
    // NamespaceFilesEditorView) now feed to translateErrorWithKey instead of the
    // null `constraints` that rendered literal ".description .details". This pins
    // the constant only; the component wiring is not mounted here (mirrors #16565).
    it("exposes a translatable key for the outdated-revision warning", async () => {
        const store = await setupOutdatedStore();

        expect(store.baseOutdatedTranslationKey).toBe(
            "outdated revision save confirmation.update",
        );
    });
});
