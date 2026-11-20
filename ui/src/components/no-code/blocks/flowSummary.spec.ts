import {describe, it, expect} from "vitest"

import {flowDescriptionOf, flowLabelEntriesOf} from "./flowSummary"

describe("flowDescriptionOf", () => {
    it("returns a string description", () => {
        expect(flowDescriptionOf({description: "Loads orders nightly"})).toBe("Loads orders nightly")
    })

    it("returns undefined when the parsed flow has no description, or the description is not a string", () => {
        expect(flowDescriptionOf(undefined)).toBeUndefined()
        expect(flowDescriptionOf({})).toBeUndefined()
        expect(flowDescriptionOf({description: 42})).toBeUndefined()
        expect(flowDescriptionOf({description: ["not", "a", "string"]})).toBeUndefined()
    })
})

describe("flowLabelEntriesOf", () => {
    it("turns a list of key/value label objects into matching entries", () => {
        const parsed = {labels: [{key: "env", value: "prod"}, {key: "team", value: "platform"}]}

        expect(flowLabelEntriesOf(parsed)).toEqual([["env", "prod"], ["team", "platform"]])
    })

    it("skips list entries that are not objects or have no key, keeping the valid ones", () => {
        const parsed = {
            labels: [
                {key: "env", value: "prod"},
                "not an object",
                42,
                null,
                {value: "orphaned"},
                {key: "team", value: "platform"},
            ],
        }

        expect(flowLabelEntriesOf(parsed)).toEqual([["env", "prod"], ["team", "platform"]])
    })

    it("turns a plain labels object into its entries", () => {
        const parsed = {labels: {env: "prod", team: "platform"}}

        expect(flowLabelEntriesOf(parsed)).toEqual([["env", "prod"], ["team", "platform"]])
    })

    it("turns a missing value into an empty string and stringifies non-string values, in both shapes", () => {
        const listParsed = {
            labels: [
                {key: "env"},
                {key: "retries", value: 0},
                {key: "active", value: false},
                {key: 404, value: "not found"},
            ],
        }

        expect(flowLabelEntriesOf(listParsed)).toEqual([
            ["env", ""],
            ["retries", "0"],
            ["active", "false"],
            ["404", "not found"],
        ])

        const mapParsed = {labels: {env: null, retries: 0, active: false}}

        expect(flowLabelEntriesOf(mapParsed)).toEqual([
            ["env", ""],
            ["retries", "0"],
            ["active", "false"],
        ])
    })

    it("returns an empty list for anything else, including no parsed flow at all", () => {
        expect(flowLabelEntriesOf(undefined)).toEqual([])
        expect(flowLabelEntriesOf({})).toEqual([])
        expect(flowLabelEntriesOf({labels: "not labels"})).toEqual([])
        expect(flowLabelEntriesOf({labels: 42})).toEqual([])
        expect(flowLabelEntriesOf({labels: null})).toEqual([])
        expect(flowLabelEntriesOf({labels: []})).toEqual([])
        expect(flowLabelEntriesOf({labels: {}})).toEqual([])
    })
})
