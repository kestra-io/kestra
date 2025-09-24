import {describe, expect, it} from "vitest";
import {decodeSearchParams, isSearchPath} from "../../../../src/components/filter/utils/helpers.ts";



describe("Params Encoding & Decoding", () => {
    it("should decode search parameters correctly", () => {
        const query = {
            "filters[namespace][EQUALS]": "test-namespace",
        };

        const decoded = decodeSearchParams(query);
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
