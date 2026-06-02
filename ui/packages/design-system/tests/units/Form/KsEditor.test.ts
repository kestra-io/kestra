import {describe, test, expect} from "vitest"
import {isOffsetInPebbleBlock, isPebbleEnabled, PEBBLE_SCHEMA_TYPES} from "../../../src/utils/pebbleBlock"
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
