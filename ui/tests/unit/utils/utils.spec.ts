import {describe, expect, it} from "vitest"
import {parsePebbleBlocks, getNumberOfNewLinesBetweenIndices} from "../../../src/utils/utils";

describe("getNumberOfNewLinesBetweenIndices", () => {
    it("should return the correct number of new lines between indices", () => {
        const text = `  Line 1
                        Line 2
                        Line 3
                        Line 4
                        Line 5`;
        const start = text.indexOf("Line 2");
        const end = text.indexOf("Line 5");
        const result = getNumberOfNewLinesBetweenIndices(text, start, end);
        expect(result).toBe(3); // There are 3 new lines between Line 2 and Line 5
    });
});

describe("parsePebbleBlocks", () => {
    it("should correctly parse a single pebble block", () => {
        const text = "Some text {{pebble.block}} more text";
        const blocks = parsePebbleBlocks(text);
        expect(blocks).toHaveLength(1);
        expect(blocks[0]).toMatchObject({
            startPos: {
                column: 13,
                lineNumber: 1,
            },
            endPos: {
                column: 27,
                lineNumber: 1,
            },
        })
    });
})