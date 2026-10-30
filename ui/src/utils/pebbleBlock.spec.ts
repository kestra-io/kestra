import {describe, expect, it} from "vitest"
import {isOffsetInPebbleBlock} from "./pebbleBlock"

describe("isOffsetInPebbleBlock", () => {
    it("returns false outside any pebble block", () => {
        const text = "hello world"
        expect(isOffsetInPebbleBlock(text, 0)).toBe(false)
        expect(isOffsetInPebbleBlock(text, 5)).toBe(false)
        expect(isOffsetInPebbleBlock(text, text.length)).toBe(false)
    })

    it("returns true between {{ and }} with a space", () => {
        // text:    {{ }}
        // offsets: 0123 4
        const text = "{{ }}"
        // cursor right after `{{` (offset 2): inside
        expect(isOffsetInPebbleBlock(text, 2)).toBe(true)
        // cursor between space and `}}` (offset 3): inside — this is the bug fixed
        expect(isOffsetInPebbleBlock(text, 3)).toBe(true)
    })

    it("returns true for the common Monaco auto-close case `{{ }}` cursor right before `}}`", () => {
        // YAML-like text Monaco produces when typing `{{` and auto-closing
        const text = "message: \"{{ }}\""
        const closingStart = text.indexOf("}}") // 13
        // cursor right before `}}` (would be the natural caret position after auto-close)
        expect(isOffsetInPebbleBlock(text, closingStart)).toBe(true)
    })

    it("returns false right after the closing }}", () => {
        const text = "{{ x }}"
        const afterClose = text.indexOf("}}") + 2
        expect(isOffsetInPebbleBlock(text, afterClose)).toBe(false)
    })

    it("handles multiple pebble blocks on one line", () => {
        const text = "{{ a }} and {{ b }}"
        const firstClose = text.indexOf("}}") // 5
        const secondOpen = text.indexOf("{{", firstClose + 2)
        // between the two blocks → false
        expect(isOffsetInPebbleBlock(text, firstClose + 2)).toBe(false)
        // inside the second block
        expect(isOffsetInPebbleBlock(text, secondOpen + 3)).toBe(true)
    })

    it("returns false when only `}}` exists before cursor", () => {
        const text = "}}abc"
        expect(isOffsetInPebbleBlock(text, 4)).toBe(false)
    })

    it("returns false at offset 0 even with `{{` later in text", () => {
        const text = "{{ x }}"
        expect(isOffsetInPebbleBlock(text, 0)).toBe(false)
    })

    it("clamps negative-style searches gracefully", () => {
        // offset 1 means searchUpTo=0; lastIndexOf at 0 only matches if `{{` starts at 0
        const text = "{{ }}"
        expect(isOffsetInPebbleBlock(text, 1)).toBe(false)
    })

    it("returns true inside statement blocks {% ... %}", () => {
        const text = "{% if inputs.foo %}"
        // cursor after inputs. (offset 13)
        expect(isOffsetInPebbleBlock(text, 13)).toBe(true)
    })

    it("returns true between {% and %} with a space", () => {
        const text = "{% %}"
        // cursor right after `{%` (offset 2): inside
        expect(isOffsetInPebbleBlock(text, 2)).toBe(true)
        // cursor between space and `%}` (offset 3): inside
        expect(isOffsetInPebbleBlock(text, 3)).toBe(true)
    })

    it("returns true for the common Monaco auto-close case `{% %}` cursor right before `%}`", () => {
        const text = "message: \"{% %}\""
        const closingStart = text.indexOf("%}")
        expect(isOffsetInPebbleBlock(text, closingStart)).toBe(true)
    })

    it("returns false right after the closing %}", () => {
        const text = "{% if x %}"
        const afterClose = text.indexOf("%}") + 2
        expect(isOffsetInPebbleBlock(text, afterClose)).toBe(false)
    })

    it("handles mixed print and statement blocks on one line", () => {
        const text = "{% for item in items %} {{ item.name }} {% endfor %}"
        const firstClose = text.indexOf("%}")
        const printOpen = text.indexOf("{{")
        const printClose = text.indexOf("}}")
        const secondStmtOpen = text.lastIndexOf("{%")

        // inside first statement block
        expect(isOffsetInPebbleBlock(text, 5)).toBe(true)
        // between first statement and print block
        expect(isOffsetInPebbleBlock(text, firstClose + 2)).toBe(false)
        // inside print block
        expect(isOffsetInPebbleBlock(text, printOpen + 3)).toBe(true)
        // between print block and second statement
        expect(isOffsetInPebbleBlock(text, printClose + 2)).toBe(false)
        // inside second statement block
        expect(isOffsetInPebbleBlock(text, secondStmtOpen + 4)).toBe(true)
    })

    it("returns false when only `%}` exists before cursor", () => {
        const text = "%}abc"
        expect(isOffsetInPebbleBlock(text, 4)).toBe(false)
    })
})
