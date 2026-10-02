import {describe, test, expect} from "vitest"
import {
    createPebbleEntryTracker,
    isOffsetInPebbleBlock,
    isPebbleEnabled,
    PEBBLE_SCHEMA_TYPES,
    pebbleBlockKeyAtOffset,
} from "../../../src/utils/pebbleBlock"

describe("isOffsetInPebbleBlock", () => {
    test("returns false outside any block", () => {
        expect(isOffsetInPebbleBlock("hello world", 0)).toBe(false)
        expect(isOffsetInPebbleBlock("hello world", 5)).toBe(false)
    })

    test("returns true inside print blocks {{ ... }}", () => {
        const text = "message: {{ inputs.name }}"
        expect(isOffsetInPebbleBlock(text, 12)).toBe(true)
        expect(isOffsetInPebbleBlock(text, 18)).toBe(true)
    })

    test("returns true inside statement blocks {% ... %}", () => {
        const text = "{% if inputs.foo %}"
        expect(isOffsetInPebbleBlock(text, 5)).toBe(true)
        expect(isOffsetInPebbleBlock(text, 13)).toBe(true)
    })

    test("returns true right after {% and right before %}", () => {
        const text = "{% %}"
        expect(isOffsetInPebbleBlock(text, 2)).toBe(true)
        expect(isOffsetInPebbleBlock(text, 3)).toBe(true)
    })

    test("returns false right after closing %}", () => {
        const text = "{% if x %} y"
        expect(isOffsetInPebbleBlock(text, 10)).toBe(false)
        expect(isOffsetInPebbleBlock(text, 11)).toBe(false)
    })

    test("handles mixed print and statement blocks on one line", () => {
        const text = "a {{ x }} b {% if y %} c"
        expect(isOffsetInPebbleBlock(text, 5)).toBe(true)
        expect(isOffsetInPebbleBlock(text, 10)).toBe(false)
        expect(isOffsetInPebbleBlock(text, 18)).toBe(true)
        expect(isOffsetInPebbleBlock(text, 23)).toBe(false)
    })
})

describe("pebbleBlockKeyAtOffset", () => {
    const text = "a {{ foo }} b"

    test("returns the offset of the opening braces for a cursor inside a block", () => {
        expect(pebbleBlockKeyAtOffset(text, 6)).toBe(2)
    })

    test("returns null for a cursor outside any block", () => {
        expect(pebbleBlockKeyAtOffset(text, 1)).toBeNull()
        expect(pebbleBlockKeyAtOffset(text, 12)).toBeNull()
    })

    test("returns null for a cursor wedged between the two opening braces", () => {
        // The leading "x" keeps the offset past the short-offset early return, so the wedge guard is what returns null.
        const wedged = "x{{}}"

        expect(pebbleBlockKeyAtOffset(wedged, 2)).toBeNull()
        expect(pebbleBlockKeyAtOffset(wedged, 3)).toBe(1)
    })

    test("gives different keys to two blocks on the same line", () => {
        const twoBlocks = "a {{ x }} {{ y }}"

        expect(pebbleBlockKeyAtOffset(twoBlocks, 5)).toBe(2)
        expect(pebbleBlockKeyAtOffset(twoBlocks, 13)).toBe(10)
    })

    test("returns the offset of opening delimiter for statement blocks {% ... %}", () => {
        const stmt = "task: {% if inputs.valid %}"
        expect(pebbleBlockKeyAtOffset(stmt, 12)).toBe(6)
    })

    test("returns null for a cursor wedged between opening characters of a statement block", () => {
        const wedged = "x{% %}"
        expect(pebbleBlockKeyAtOffset(wedged, 2)).toBeNull()
        expect(pebbleBlockKeyAtOffset(wedged, 3)).toBe(1)
    })

    test("gives correct keys to mixed print and statement blocks on the same line", () => {
        const mixed = "a {{ x }} b {% for item in items %} c"
        expect(pebbleBlockKeyAtOffset(mixed, 5)).toBe(2)
        expect(pebbleBlockKeyAtOffset(mixed, 10)).toBeNull()
        expect(pebbleBlockKeyAtOffset(mixed, 20)).toBe(12)
        expect(pebbleBlockKeyAtOffset(mixed, 36)).toBeNull()
    })
})

describe("createPebbleEntryTracker", () => {
    test("reports a fresh entry once, then clears", () => {
        const tracker = createPebbleEntryTracker()

        tracker.track(2)

        expect(tracker.consumeEntered()).toBe(true)
        expect(tracker.consumeEntered()).toBe(false)
    })

    test("does not report staying in the same block as a fresh entry", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(2)
        tracker.consumeEntered()

        tracker.track(2)
        tracker.track(2)

        expect(tracker.consumeEntered()).toBe(false)
    })

    test("does not report leaving a block as a fresh entry", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(2)
        tracker.consumeEntered()

        tracker.track(null)

        expect(tracker.consumeEntered()).toBe(false)
    })

    test("reports moving straight from one block to another as a fresh entry", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(2)
        tracker.consumeEntered()

        tracker.track(10)

        expect(tracker.consumeEntered()).toBe(true)
    })

    test("reports leaving a block and returning within one burst as a fresh entry", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(2)
        tracker.consumeEntered()

        tracker.track(null)
        tracker.track(2)

        expect(tracker.consumeEntered()).toBe(true)
    })
})

describe("isPebbleEnabled", () => {
    test("an explicit pebble flag wins over everything else", () => {
        expect(isPebbleEnabled({pebble: false, lang: "yaml-pebble", schemaType: "flow"})).toBe(false)
        expect(isPebbleEnabled({pebble: true, lang: "json"})).toBe(true)
    })

    test("enables the yaml-pebble language without an explicit flag", () => {
        expect(isPebbleEnabled({lang: "yaml-pebble"})).toBe(true)
    })

    test.each(Array.from(PEBBLE_SCHEMA_TYPES))("enables the %s schema type", (schemaType) => {
        expect(isPebbleEnabled({lang: "json", schemaType})).toBe(true)
    })

    test("falls back to plain yaml", () => {
        expect(isPebbleEnabled({lang: "yaml"})).toBe(true)
    })

    test("stays disabled for any other language and schema type", () => {
        expect(isPebbleEnabled({lang: "json", schemaType: "other"})).toBe(false)
        expect(isPebbleEnabled({})).toBe(false)
    })
})
