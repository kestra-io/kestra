import {describe, test, expect} from "vitest"
import {createPebbleEntryTracker, isOffsetInPebbleBlock, isPebbleEnabled, pebbleBlockKeyAtOffset, PEBBLE_SCHEMA_TYPES} from "../../../src/utils/pebbleBlock"
import {findDuplicateTaskIds} from "../../../src/utils/yamlValidation"

describe("KsEditor / pebbleBlock", () => {
    test("returns false for offset < 2", () => {
        expect(isOffsetInPebbleBlock("{{ x }}", 0)).toBe(false)
        expect(isOffsetInPebbleBlock("{{ x }}", 1)).toBe(false)
    })

    test("detects offset inside {{ ... }}", () => {
        const text = "name = {{ flow.id }}"
        // index of "flow" letter "f"
        const offset = text.indexOf("flow")
        expect(isOffsetInPebbleBlock(text, offset)).toBe(true)
    })

    test("offset before first {{ is not inside a block", () => {
        const text = "abc = {{ flow.id }}"
        expect(isOffsetInPebbleBlock(text, 2)).toBe(false)
    })

    test("offset after }} is not inside a block", () => {
        const text = "{{ a }} after"
        expect(isOffsetInPebbleBlock(text, text.length - 2)).toBe(false)
    })

    test("text with no pebble at all", () => {
        expect(isOffsetInPebbleBlock("no braces here", 5)).toBe(false)
    })
})

describe("KsEditor / findDuplicateTaskIds", () => {
    test("returns empty array for valid flow without duplicates", () => {
        const yaml = `
id: my-flow
namespace: company.team
tasks:
  - id: t1
    type: io.kestra.plugin.core.log.Log
  - id: t2
    type: io.kestra.plugin.core.log.Log
`
        expect(findDuplicateTaskIds(yaml)).toEqual([])
    })

    test("detects a single duplicate task id", () => {
        const yaml = `
id: my-flow
namespace: company.team
tasks:
  - id: same
    type: io.kestra.plugin.core.log.Log
  - id: same
    type: io.kestra.plugin.core.log.Log
`
        const markers = findDuplicateTaskIds(yaml)
        expect(markers.length).toBe(1)
        expect(markers[0].taskId).toBe("same")
        expect(markers[0].severity).toBe("error")
        expect(markers[0].message).toContain("same")
    })

    test("detects duplicates inside nested tasks/errors", () => {
        const yaml = `
id: my-flow
tasks:
  - id: outer
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: dup
        type: io.kestra.plugin.core.log.Log
errors:
  - id: dup
    type: io.kestra.plugin.core.log.Log
`
        const markers = findDuplicateTaskIds(yaml)
        const ids = markers.map(m => m.taskId)
        expect(ids).toContain("dup")
    })

    test("handles invalid yaml without throwing", () => {
        const yaml = ":::: not yaml ::::"
        expect(() => findDuplicateTaskIds(yaml)).not.toThrow()
    })
})

describe("KsEditor / pebbleBlockKeyAtOffset", () => {
    //               0         1
    //               0123456789012345678
    const text =    "a = {{ x }} {{ y }}"

    test("returns null outside any block", () => {
        expect(pebbleBlockKeyAtOffset(text, 2)).toBe(null)
    })

    test("returns the opening `{{` offset for the containing block", () => {
        expect(pebbleBlockKeyAtOffset(text, text.indexOf("x"))).toBe(4)
        expect(pebbleBlockKeyAtOffset(text, text.indexOf("y"))).toBe(12)
    })

    test("distinct blocks yield distinct keys", () => {
        const a = pebbleBlockKeyAtOffset(text, text.indexOf("x"))
        const b = pebbleBlockKeyAtOffset(text, text.indexOf("y"))
        expect(a).not.toBe(b)
    })

    // Regression for kestra #14989: the cursor wedged between the two opening braces (`{|{}}`)
    // must NOT count as being in the block. It resolves to the same nearest `{{` as `{{|}}`,
    // so without this distinction moving `{{|}}` -> `{|{}}` -> `{{|}}` looks like "never left"
    // and autocomplete never reopens.
    test("cursor between the opening braces is not in the block", () => {
        const empty = "x = {{}}"          // `{{` at 4, `}}` at 6
        expect(pebbleBlockKeyAtOffset(empty, 6)).toBe(4)   // {{|}}  -> in body
        expect(pebbleBlockKeyAtOffset(empty, 5)).toBe(null) // {|{}}  -> wedged in `{{`
        expect(pebbleBlockKeyAtOffset(empty, 7)).toBe(null) // {{}|}  -> wedged in `}}`
    })
})

describe("KsEditor / createPebbleEntryTracker", () => {
    test("detects a fresh entry into a Pebble block", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(null)
        tracker.track(10)
        expect(tracker.consumeEntered()).toBe(true)
    })

    test("consumeEntered resets the flag", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(null)
        tracker.track(10)
        expect(tracker.consumeEntered()).toBe(true)
        expect(tracker.consumeEntered()).toBe(false)
    })

    test("no entry while staying outside a Pebble block", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(null)
        tracker.track(null)
        expect(tracker.consumeEntered()).toBe(false)
    })

    test("no fresh entry while staying inside the same block", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(10)
        expect(tracker.consumeEntered()).toBe(true)
        // cursor keeps moving but stays in the same block (same key) — should not re-trigger
        tracker.track(10)
        tracker.track(10)
        expect(tracker.consumeEntered()).toBe(false)
    })

    // Regression for kestra #14989: a fast move out of and back into a `{{ }}` block,
    // collapsed into a single debounced settle, must still register as a fresh entry.
    // The previous implementation updated the latch only inside the debounced callback,
    // so the intermediate "out" position was swallowed and autocomplete never reopened.
    test("detects re-entry after a fast out-and-back round-trip", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(10) // already inside
        expect(tracker.consumeEntered()).toBe(true)
        // within a single debounce burst: leave, then return to the same block
        tracker.track(null)
        tracker.track(10)
        expect(tracker.consumeEntered()).toBe(true)
    })

    // Regression for kestra #14989 follow-up: moving straight from one `{{ }}` block to a
    // different one (no non-block position in between) is a fresh entry too. A boolean
    // in/out latch missed this because "in pebble" never flipped to false.
    test("detects moving directly from one block to another", () => {
        const tracker = createPebbleEntryTracker()
        tracker.track(10) // in block A
        expect(tracker.consumeEntered()).toBe(true)
        tracker.track(30) // straight into block B
        expect(tracker.consumeEntered()).toBe(true)
    })
})

describe("KsEditor / isPebbleEnabled", () => {
    test("whitelist is exactly {flow, dashboard, app, testsuites}", () => {
        expect(PEBBLE_SCHEMA_TYPES).toEqual(["flow", "dashboard", "app", "testsuites"])
    })

    test.each(PEBBLE_SCHEMA_TYPES)("enabled for whitelisted schemaType %s", (s) => {
        expect(isPebbleEnabled({schemaType: s})).toBe(true)
    })

    test.each([
        "section",
        "task",
        "trigger",
        "apps",
        "App",
        "FLOW",
        "",
        undefined,
    ])("disabled for non-whitelisted schemaType %s", (s) => {
        expect(isPebbleEnabled({schemaType: s})).toBe(false)
    })

    test("explicit pebble=true overrides everything", () => {
        expect(isPebbleEnabled({pebble: true})).toBe(true)
        expect(isPebbleEnabled({pebble: true, schemaType: "section"})).toBe(true)
        expect(isPebbleEnabled({pebble: true, schemaType: "apps"})).toBe(true)
    })

    test("explicit pebble=false overrides everything", () => {
        expect(isPebbleEnabled({pebble: false})).toBe(false)
        expect(isPebbleEnabled({pebble: false, schemaType: "flow"})).toBe(false)
        expect(isPebbleEnabled({pebble: false, lang: "yaml-pebble"})).toBe(false)
    })

    test("lang=yaml-pebble forces pebble on", () => {
        expect(isPebbleEnabled({lang: "yaml-pebble"})).toBe(true)
        expect(isPebbleEnabled({lang: "yaml-pebble", schemaType: "section"})).toBe(true)
    })

    test("lang=yaml enables pebble by default (parity with pre-migration Editor.vue)", () => {
        expect(isPebbleEnabled({lang: "yaml"})).toBe(true)
        expect(isPebbleEnabled({lang: "yaml", schemaType: "section"})).toBe(true)
    })

    test("non-yaml langs do not enable pebble", () => {
        expect(isPebbleEnabled({lang: "json"})).toBe(false)
        expect(isPebbleEnabled({lang: "python"})).toBe(false)
        expect(isPebbleEnabled({lang: "plaintext"})).toBe(false)
    })
})
