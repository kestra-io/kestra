import {describe, expect, it, vi, beforeEach} from "vitest"
import {PebbleAutoCompletion, resetExpressionCache, functionToSnippet} from "../../../src/services/autoCompletionProvider";

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

    it("functionsWithDefaults fetches functions list from API", async () => {
        const functions = [
            {name: "kv", arguments: [{name: "key", defaultValue: "'my_key'"}, {name: "namespace", defaultValue: "flow.namespace"}, {name: "errorOnMissing", defaultValue: null}]},
            {name: "now", arguments: [{name: "format", defaultValue: null}]},
            {name: "secret", arguments: [{name: "key", defaultValue: "'MY_SECRET'"}]},
            {name: "uuid", arguments: []},
        ];
        axiosGet.mockResolvedValue({data: functions});

        const provider = new PebbleAutoCompletion();
        const result = await provider.functionsWithDefaults();

        expect(axiosGet).toHaveBeenCalledWith("http://localhost/api/v1/pebble/functions");
        expect(result).toEqual(functions);
    });

    it("filterAutoCompletion returns empty array on API error", async () => {
        axiosGet.mockRejectedValue(new Error("Network error"));

        const provider = new PebbleAutoCompletion();
        const result = await provider.filterAutoCompletion();

        expect(result).toEqual([]);
    });

    describe("functionToSnippet", () => {
        it("returns bare call for function with no arguments", () => {
            expect(functionToSnippet({name: "uuid", arguments: []})).toBe("uuid()");
        });

        it("returns bare call when all arguments have null defaults", () => {
            expect(functionToSnippet({
                name: "now",
                arguments: [{name: "format", defaultValue: null}],
            })).toBe("now()");
        });

        it("returns snippet with named args for kv function", () => {
            const fn = {
                name: "kv",
                arguments: [
                    {name: "key", defaultValue: "'my_key'"},
                    {name: "namespace", defaultValue: "flow.namespace"},
                    {name: "errorOnMissing", defaultValue: null},
                ],
            };
            expect(functionToSnippet(fn)).toBe("kv(key=${1:'my_key'}, namespace=${2:flow.namespace})");
        });

        it("returns snippet with single named arg for secret function", () => {
            const fn = {
                name: "secret",
                arguments: [{name: "key", defaultValue: "'MY_SECRET'"}],
            };
            expect(functionToSnippet(fn)).toBe("secret(key=${1:'MY_SECRET'})");
        });
    });

    it("functionsWithDefaults returns empty array on API error", async () => {
        axiosGet.mockRejectedValue(new Error("Network error"));

        const provider = new PebbleAutoCompletion();
        const result = await provider.functionsWithDefaults();

        expect(result).toEqual([]);
    });
});
