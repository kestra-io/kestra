import {describe, expect, it, vi, beforeEach} from "vitest";
import {setActivePinia, createPinia} from "pinia";

const axiosGet = vi.fn();
const axiosDelete = vi.fn();

vi.mock("../../../src/utils/axios", () => ({
    useAxios: () => ({
        get: axiosGet,
        delete: axiosDelete,
    }),
}));

vi.mock("../../../src/override/utils/route", () => ({
    apiUrl: () => "http://localhost/api/v1/main",
}));

describe("logs store", () => {
    beforeEach(() => {
        axiosGet.mockReset();
        axiosDelete.mockReset();
        setActivePinia(createPinia());
    });

    it("findLogs populates logs and total from the response", async () => {
        const {useLogsStore} = await import("../../../src/stores/logs");
        const store = useLogsStore();

        axiosGet.mockResolvedValue({
            data: {
                results: [{id: "k1", level: "INFO"}, {id: "k2", level: "WARN"}],
                total: 2,
            },
        });

        await store.findLogs({page: 1, size: 25});

        expect(axiosGet).toHaveBeenCalledWith(
            "http://localhost/api/v1/main/logs/search",
            {params: {page: 1, size: 25}},
        );
        expect(store.logs).toHaveLength(2);
        expect(store.logs?.[0].id).toBe("k1");
        expect(store.total).toBe(2);
    });

    it("bulkDeleteLogs sends the ids in the request body to /by-ids", async () => {
        const {useLogsStore} = await import("../../../src/stores/logs");
        const store = useLogsStore();

        axiosDelete.mockResolvedValue({data: {count: 2}});

        const ids = ["key-a", "key-b"];
        await store.bulkDeleteLogs(ids);

        expect(axiosDelete).toHaveBeenCalledWith(
            "http://localhost/api/v1/main/logs/by-ids",
            {data: ids},
        );
    });

    it("queryDeleteLogs sends filters as query params to /by-query", async () => {
        const {useLogsStore} = await import("../../../src/stores/logs");
        const store = useLogsStore();

        axiosDelete.mockResolvedValue({data: undefined});

        const filters = {
            "filters[level][EQUALS]": "WARN",
            "filters[timeRange][EQUALS]": "PT24H",
        };
        await store.queryDeleteLogs(filters);

        expect(axiosDelete).toHaveBeenCalledWith(
            "http://localhost/api/v1/main/logs/by-query",
            {params: filters},
        );
    });

    it("bulkDeleteLogs and queryDeleteLogs target distinct endpoints", async () => {
        const {useLogsStore} = await import("../../../src/stores/logs");
        const store = useLogsStore();

        axiosDelete.mockResolvedValue({data: {}});

        await store.bulkDeleteLogs(["k1"]);
        await store.queryDeleteLogs({});

        const urls = axiosDelete.mock.calls.map(call => call[0]);
        expect(urls).toContain("http://localhost/api/v1/main/logs/by-ids");
        expect(urls).toContain("http://localhost/api/v1/main/logs/by-query");
    });
});
