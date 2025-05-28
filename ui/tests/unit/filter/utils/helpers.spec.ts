import {describe, expect, it} from "vitest";
import {decodeSearchParams, encodeSearchParams, isSearchPath} from "../../../../src/components/filter/utils/helpers.ts";


const COMPARATORS = {
    EQUALS: {label: "is", value: "EQUALS"}
};

const OPTIONS = [
    {key: "namespace", label: "Namespace", value: {label: "namespace", comparator: undefined, value: []}, comparators: [COMPARATORS.EQUALS]},
    {key: "state", label: "State", value: {label: "state", comparator: undefined, value: []}, comparators: [COMPARATORS.EQUALS]},
];



describe("Params Encoding & Decoding", () => {


    it("should encode search parameters correctly", () => {
        const filters = [
            {label: "namespace", value: ["test-namespace"]},
            {label: "state", value: ["active"]},
        ];

        const encoded = encodeSearchParams(filters, OPTIONS);
        expect(encoded).toHaveProperty("filters[namespace][EQUALS]");
    });

    it("should decode search parameters correctly", () => {
        const query = {
            "filters[namespace][EQUALS]": "test-namespace",
        };

        const decoded = decodeSearchParams(query, ["namespace"], OPTIONS);
        expect(decoded).toEqual([
            {field: "namespace", value: "test-namespace", operation: "EQUALS"},
        ]);
    });

    it("should identify search paths correctly", () => {
        expect(isSearchPath( "flows/list")).toBe(true);
        expect(isSearchPath("executions/list")).toBe(true);
        expect(isSearchPath("/unknown")).toBe(false);
    });
});
