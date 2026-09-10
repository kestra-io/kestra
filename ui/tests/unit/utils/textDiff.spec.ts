import {describe, it, expect} from "vitest"
import {diffLines, summarizeDiff, collapseContext} from "../../../src/utils/textDiff"

describe("textDiff", () => {
    describe("diffLines", () => {
        it("returns every line as context when the texts are identical", () => {
            const entries = diffLines("a\nb\nc", "a\nb\nc")
            expect(entries).toEqual([
                {kind: "context", text: "a"},
                {kind: "context", text: "b"},
                {kind: "context", text: "c"},
            ])
        })

        it("marks every line as added when there's nothing to diff against", () => {
            const entries = diffLines("", "a\nb")
            expect(entries).toEqual([
                {kind: "added", text: "a"},
                {kind: "added", text: "b"},
            ])
        })

        it("marks every line as removed when the new content is empty", () => {
            const entries = diffLines("a\nb", "")
            expect(entries).toEqual([
                {kind: "removed", text: "a"},
                {kind: "removed", text: "b"},
            ])
        })

        it("finds a minimal edit for a single changed line in the middle", () => {
            const entries = diffLines("a\nb\nc", "a\nx\nc")
            expect(entries).toEqual([
                {kind: "context", text: "a"},
                {kind: "removed", text: "b"},
                {kind: "added", text: "x"},
                {kind: "context", text: "c"},
            ])
        })

        it("detects a pure insertion", () => {
            const entries = diffLines("a\nc", "a\nb\nc")
            expect(entries).toEqual([
                {kind: "context", text: "a"},
                {kind: "added", text: "b"},
                {kind: "context", text: "c"},
            ])
        })
    })

    describe("summarizeDiff", () => {
        it("counts added and removed lines only", () => {
            const entries = diffLines("a\nb\nc", "a\nx\nc\nd")
            expect(summarizeDiff(entries)).toEqual({added: 2, removed: 1})
        })

        it("is zero for identical content", () => {
            expect(summarizeDiff(diffLines("a\nb", "a\nb"))).toEqual({added: 0, removed: 0})
        })
    })

    describe("collapseContext", () => {
        it("keeps a short unchanged run untouched", () => {
            const entries = diffLines("a\nb\nc\nd", "a\nx\nc\nd")
            expect(collapseContext(entries, 3)).toEqual(entries)
        })

        it("collapses a long unchanged run down to a gap, keeping context on each side", () => {
            const before = Array.from({length: 10}, (_, i) => `line${i}`).join("\n")
            const after = before.replace("line5", "changed")
            const entries = diffLines(before, after)

            const hunks = collapseContext(entries, 2)

            expect(hunks[0]).toEqual({kind: "gap", count: 3}) // lines 0-2 hidden, keeping 3-4 as context
            expect(hunks.find((h) => h.kind === "removed")).toEqual({kind: "removed", text: "line5"})
            expect(hunks.find((h) => h.kind === "added")).toEqual({kind: "added", text: "changed"})
            expect(hunks[hunks.length - 1]).toEqual({kind: "gap", count: 2}) // line8-line9 hidden after 2 lines of leading context (line6, line7); no trailing context at file end
        })

        it("keeps no leading context before the very first change", () => {
            const before = "a\nb\nc\nd\ne"
            const after = "x\nb\nc\nd\ne"
            const hunks = collapseContext(diffLines(before, after), 2)
            expect(hunks[0]).toEqual({kind: "removed", text: "a"})
            expect(hunks[1]).toEqual({kind: "added", text: "x"})
        })
    })
})
