import {beforeEach, describe, expect, it, vi} from "vitest";
import {createPinia, setActivePinia} from "pinia";

const axiosGet = vi.fn();

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}));

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}));

vi.mock("../../../src/utils/axios", () => ({
    useAxios: () => ({get: axiosGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}));

const {useExecutionsStore} = await import("../../../src/stores/executions");

const page = (ids: string[]) => ({data: {results: ids.map(id => ({id, namespace: "ns"})), total: ids.length}});

describe("executions search ordering", () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        axiosGet.mockReset();
    });

    it("drops an executions search that answers after a newer one", async () => {
        const store = useExecutionsStore();

        let resolveStale: (value: unknown) => void = () => {};
        axiosGet
            .mockImplementationOnce(() => new Promise((resolve) => {
                resolveStale = resolve;
            }))
            .mockImplementationOnce(() => Promise.resolve(page(["newest"])));

        const stale = store.findExecutions({page: 1, size: 25});
        const newest = store.findExecutions({page: 1, size: 25});
        resolveStale(page(["stale"]));
        await Promise.all([stale, newest]);

        expect(store.executions?.map(execution => execution.id)).toEqual(["newest"]);
        expect(store.total).toBe(1);
    });

    it("still commits an in-flight executions search when a non-committing fetch runs in between", async () => {
        const store = useExecutionsStore();

        let resolveSearch: (value: unknown) => void = () => {};
        axiosGet
            .mockImplementationOnce(() => new Promise((resolve) => {
                resolveSearch = resolve;
            }))
            .mockImplementationOnce(() => Promise.resolve(page(["drill-down"])));

        const inFlight = store.findExecutions({page: 1, size: 25});
        await store.findExecutions({page: 1, size: 25, commit: false});
        resolveSearch(page(["list"]));
        await inFlight;

        expect(store.executions?.map(execution => execution.id)).toEqual(["list"]);
    });

    it("waits for a slow executions search instead of timing out", async () => {
        const store = useExecutionsStore();
        axiosGet.mockResolvedValueOnce(page(["slow"]));

        await store.findExecutions({page: 1, size: 25});

        expect(axiosGet.mock.calls[0][1]).toMatchObject({timeout: 0});
    });
});
