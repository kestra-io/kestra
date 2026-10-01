import {describe, test, expect} from "vitest"
import {
    createPebbleEntryTracker,
    isPebbleEnabled,
    PEBBLE_SCHEMA_TYPES,
    pebbleBlockKeyAtOffset,
} from "../../../src/utils/pebbleBlock"

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
