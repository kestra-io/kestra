// Pinned east of UTC: the DATE round-trip only breaks there, and CI runs in UTC.
process.env.TZ = "Europe/Paris"

import {describe, test, expect} from "vitest"
import moment from "moment-timezone"

import {formatKvValueForDisplay, hydrateKvValueForForm, serializeKvValueForSave} from "../../../../src/components/kv/kvValue"

describe("formatKvValueForDisplay", () => {
    test("STRING is rendered as-is", () => {
        expect(formatKvValueForDisplay("STRING", "hello")).toBe("hello")
    })

    test("NUMBER is stringified", () => {
        expect(formatKvValueForDisplay("NUMBER", 42)).toBe("42")
    })

    test("BOOLEAN is stringified", () => {
        expect(formatKvValueForDisplay("BOOLEAN", true)).toBe("true")
    })

    test("JSON is pretty-printed", () => {
        expect(formatKvValueForDisplay("JSON", {a: 1})).toBe("{\n  \"a\": 1\n}")
    })

    test("DATETIME is formatted in the provided timezone", () => {
        const utc = formatKvValueForDisplay("DATETIME", "2024-01-01T00:00:00Z", "UTC")
        expect(utc).toContain("2024-01-01")
        const tokyo = formatKvValueForDisplay("DATETIME", "2024-01-01T00:00:00Z", "Asia/Tokyo")
        expect(tokyo).toContain("2024-01-01T09:00:00")
    })
})

describe("serializeKvValueForSave", () => {
    test("should quote a STRING so the backend types it back as a string", () => {
        expect(serializeKvValueForSave("STRING", "hello")).toBe("\"hello\"")
    })

    test("should keep the surrounding whitespace of a STRING", () => {
        expect(serializeKvValueForSave("STRING", "  padded  ")).toBe("\"  padded  \"")
    })

    test("should quote a STRING that looks like a number or a boolean", () => {
        expect(serializeKvValueForSave("STRING", "42")).toBe("\"42\"")
        expect(serializeKvValueForSave("STRING", "true")).toBe("\"true\"")
    })

    test("should escape a STRING containing quotes and newlines", () => {
        expect(serializeKvValueForSave("STRING", "a \"quoted\"\nline")).toBe("\"a \\\"quoted\\\"\\nline\"")
    })

    test("should send a NUMBER unquoted", () => {
        expect(serializeKvValueForSave("NUMBER", "42")).toBe("42")
        expect(serializeKvValueForSave("NUMBER", 1.5)).toBe("1.5")
    })

    test("should send a BOOLEAN unquoted for both values", () => {
        expect(serializeKvValueForSave("BOOLEAN", true)).toBe("true")
        expect(serializeKvValueForSave("BOOLEAN", false)).toBe("false")
    })

    test("should send a DATETIME as an UTC instant", () => {
        expect(serializeKvValueForSave("DATETIME", new Date("2024-01-01T10:30:00Z"))).toBe("2024-01-01T10:30:00.000Z")
    })

    test("should send the picked calendar day for a DATE, whatever the timezone", () => {
        // The picker yields local midnight; UTC conversion would roll it back a day east of UTC.
        expect(serializeKvValueForSave("DATE", new Date(2024, 0, 1, 0, 0, 0))).toBe("2024-01-01")
        expect(serializeKvValueForSave("DATE", new Date(2024, 11, 31, 23, 59, 59))).toBe("2024-12-31")
    })

    test("should send a DURATION as its ISO 8601 text", () => {
        expect(serializeKvValueForSave("DURATION", "PT45M")).toBe("PT45M")
    })

    test("should send JSON as the raw editor text", () => {
        expect(serializeKvValueForSave("JSON", "{\"a\":1}")).toBe("{\"a\":1}")
    })

    test("should fall back to an empty payload for an untouched DURATION or JSON", () => {
        expect(serializeKvValueForSave("DURATION", undefined)).toBe("")
        expect(serializeKvValueForSave("JSON", undefined)).toBe("")
    })
})

describe("hydrateKvValueForForm", () => {
    test("should feed a STRING to the textarea unchanged", () => {
        expect(hydrateKvValueForForm("STRING", "  padded  ")).toBe("  padded  ")
    })

    test("should feed a NUMBER to the number input as text", () => {
        expect(hydrateKvValueForForm("NUMBER", 42)).toBe("42")
    })

    test("should keep a BOOLEAN as a boolean for the switch", () => {
        expect(hydrateKvValueForForm("BOOLEAN", false)).toBe(false)
        expect(hydrateKvValueForForm("BOOLEAN", true)).toBe(true)
    })

    test("should feed a DATETIME to the picker in the user timezone", () => {
        const date = hydrateKvValueForForm("DATETIME", "2024-01-01T00:00:00Z", "Asia/Tokyo")
        expect(moment(date).tz("Asia/Tokyo").format("YYYY-MM-DDTHH:mm")).toBe("2024-01-01T09:00")
    })

    test("should feed a DATE to the picker as its calendar day", () => {
        expect(hydrateKvValueForForm("DATE", "2024-01-01")).toBe("2024-01-01")
    })

    test("should feed a DURATION to the input as its ISO 8601 text", () => {
        expect(hydrateKvValueForForm("DURATION", "PT45M")).toBe("PT45M")
    })

    test("should feed JSON to the editor as compact text", () => {
        expect(hydrateKvValueForForm("JSON", {a: 1})).toBe("{\"a\":1}")
    })
})

describe("KV value round-trip", () => {
    // What the API gives back for each type once the ION payload has been parsed server-side.
    const roundTrips: {type: string; saved: any; returned: any}[] = [
        {type: "STRING", saved: "  padded  ", returned: "  padded  "},
        {type: "STRING", saved: "42", returned: "42"},
        {type: "NUMBER", saved: "42", returned: 42},
        {type: "BOOLEAN", saved: true, returned: true},
        {type: "BOOLEAN", saved: false, returned: false},
        {type: "DURATION", saved: "PT45M", returned: "PT45M"},
    ]

    test.each(roundTrips)("should reopen a $type as it was saved", ({type, saved, returned}) => {
        expect(hydrateKvValueForForm(type, returned)).toStrictEqual(saved)
    })

    test("should reopen a DATE on the day it was saved", () => {
        const picked = new Date(2024, 0, 1, 0, 0, 0)
        expect(hydrateKvValueForForm("DATE", serializeKvValueForSave("DATE", picked))).toBe("2024-01-01")
    })

    test("should reopen a DATETIME at the instant it was saved", () => {
        const picked = new Date("2024-01-01T10:30:00Z")
        const stored = serializeKvValueForSave("DATETIME", picked)
        expect(hydrateKvValueForForm("DATETIME", stored, "Asia/Tokyo").getTime()).toBe(picked.getTime())
    })
})
