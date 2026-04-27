import {describe, expect, it, vi, beforeEach} from "vitest";
import {setActivePinia, createPinia} from "pinia";

const deleteLogsByIds = vi.fn();
const deleteLogsByQuery = vi.fn();

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: vi.fn(),
    deleteLogsFromFlow: vi.fn(),
    deleteLogsByIds: (...args: any[]) => deleteLogsByIds(...args),
    deleteLogsByQuery: (...args: any[]) => deleteLogsByQuery(...args),
}));

describe("logs store bulk delete", () => {
    beforeEach(() => {
        vi.resetModules();
        deleteLogsByIds.mockReset();
        deleteLogsByQuery.mockReset();
        setActivePinia(createPinia());
    });

    it("bulkDeleteLogs sends the ids in the request body", async () => {
        const {useLogsStore} = await import("../../../src/stores/logs");
        const store = useLogsStore();

        deleteLogsByIds.mockResolvedValue({count: 2});

        const ids = ["key-a", "key-b"];
        await store.bulkDeleteLogs(ids);

        expect(deleteLogsByIds).toHaveBeenCalledWith(expect.objectContaining({body: ids}));
    });

    it("queryDeleteLogs sends the route filters as a QueryFilter array", async () => {
        const {useLogsStore} = await import("../../../src/stores/logs");
        const store = useLogsStore();

        deleteLogsByQuery.mockResolvedValue(undefined);

        await store.queryDeleteLogs({
            "filters[level][EQUALS]": "WARN",
            "filters[timeRange][EQUALS]": "PT24H",
        });

        expect(deleteLogsByQuery).toHaveBeenCalledWith(expect.objectContaining({
            filters: expect.arrayContaining([
                expect.objectContaining({field: "level", operation: "EQUALS", value: "WARN"}),
            ]),
        }));
    });

    it("bulkDeleteLogs and queryDeleteLogs call distinct SDK functions", async () => {
        const {useLogsStore} = await import("../../../src/stores/logs");
        const store = useLogsStore();

        deleteLogsByIds.mockResolvedValue({count: 1});
        deleteLogsByQuery.mockResolvedValue(undefined);

        await store.bulkDeleteLogs(["k1"]);
        await store.queryDeleteLogs({});

        expect(deleteLogsByIds).toHaveBeenCalledTimes(1);
        expect(deleteLogsByQuery).toHaveBeenCalledTimes(1);
    });
});
