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
                column: 11,
                lineNumber: 1,
            },
            endPos: {
                column: 25,
                lineNumber: 1,
            },
        })
    });

    it("should correctly parse multiple pebble blocks", () => {
        const text = "Start {{block.one}} middle {{block.two}} end";
        const blocks = parsePebbleBlocks(text);
        expect(blocks).toHaveLength(2);
        expect(blocks[0]).toMatchObject({
            startPos: {
                column: 7,
                lineNumber: 1,
            },
            endPos: {
                column: 18,
                lineNumber: 1,
            },
        });
        expect(blocks[1]).toMatchObject({
            startPos: {
                column: 28,
                lineNumber: 1,
            },
            endPos: {
                column: 39,
                lineNumber: 1,
            },
        });
    });

    it("should correctly parse pebble blocks across multiple lines", () => {
        const text = `Line 1
                        {{block.one}}
                        Line 3
                        {{block.two}}
                        Line 5`;
        const blocks = parsePebbleBlocks(text);
        expect(blocks).toHaveLength(2);
        expect(blocks[0]).toMatchObject({
            startPos: {
                column: 25,
                lineNumber: 2,
            },
            endPos: {
                column: 36,
                lineNumber: 2,
            },
        });
        expect(blocks[1]).toMatchObject({
            startPos: {
                column: 25,
                lineNumber: 4,
            },
            endPos: {
                column: 36,
                lineNumber: 4,
            },
        });
    });

    it("should handle unclosed pebble blocks", () => {
        const text = "Some text {{pebble.block more text";
        const blocks = parsePebbleBlocks(text);
        expect(blocks).toHaveLength(1);
        expect(blocks[0]).toMatchObject({
            startPos: {
                column: 11,
                lineNumber: 1,
            },
            endPos: {
                column: 35,
                lineNumber: 1,
            },
        });
    });
})