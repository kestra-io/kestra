import {describe, expect, it, vi, beforeEach} from "vitest"
import {PebbleAutoCompletion, resetExpressionCache} from "../../../src/services/autoCompletionProvider";

const axiosGet = vi.fn();

vi.mock("../../../src/utils/axios", () => ({
    useAxios: () => ({
        get: axiosGet,
    }),
}));

vi.mock("../../../src/override/utils/route", () => ({
    apiUrlWithoutTenants: () => "http://localhost/api/v1",
}));

describe("PebbleAutoCompletion", () => {
    beforeEach(() => {
        axiosGet.mockReset();
        resetExpressionCache();
    });

    it("filterAutoCompletion fetches filters from API", async () => {
        const filters = ["abs", "capitalize", "jq", "toJson", "upper", "yaml"];
        axiosGet.mockResolvedValue({data: filters});

        const provider = new PebbleAutoCompletion();
        const result = await provider.filterAutoCompletion();

        expect(axiosGet).toHaveBeenCalledWith("http://localhost/api/v1/pebble/filters");
        expect(result).toEqual(filters);
    });

    it("functionNames fetches functions from API", async () => {
        const functions = ["json", "kv", "max", "min", "now", "secret", "uuid"];
        axiosGet.mockResolvedValue({data: functions});

        const provider = new PebbleAutoCompletion();
        const result = await provider.functionNames();

        expect(axiosGet).toHaveBeenCalledWith("http://localhost/api/v1/pebble/functions");
        expect(result).toEqual(functions);
    });

    it("filterAutoCompletion returns empty array on API error", async () => {
        axiosGet.mockRejectedValue(new Error("Network error"));

        const provider = new PebbleAutoCompletion();
        const result = await provider.filterAutoCompletion();

        expect(result).toEqual([]);
    });

    it("functionNames returns empty array on API error", async () => {
        axiosGet.mockRejectedValue(new Error("Network error"));

        const provider = new PebbleAutoCompletion();
        const result = await provider.functionNames();

        expect(result).toEqual([]);
    });
});
