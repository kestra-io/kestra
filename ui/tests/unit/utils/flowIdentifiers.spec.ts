import {describe, test, expect} from "vitest"
import {isValidFlowId, isValidNamespace} from "../../../src/utils/flowIdentifiers"

describe("isValidFlowId", () => {
    test("accepts the identifiers the backend accepts", () => {
        expect(isValidFlowId("my-flow")).toBe(true)
        expect(isValidFlowId("my_flow.v2")).toBe(true)
        expect(isValidFlowId("Flow1")).toBe(true)
    })

    test("rejects identifiers that would break the generated YAML", () => {
        expect(isValidFlowId("my flow")).toBe(false)
        expect(isValidFlowId("my-flow: injected")).toBe(false)
        expect(isValidFlowId("my-flow\ndescription: injected")).toBe(false)
        expect(isValidFlowId("-leading-dash")).toBe(false)
        expect(isValidFlowId("")).toBe(false)
    })

    test("rejects identifiers longer than 100 characters", () => {
        expect(isValidFlowId("a".repeat(100))).toBe(true)
        expect(isValidFlowId("a".repeat(101))).toBe(false)
    })
})

describe("isValidNamespace", () => {
    test("accepts lowercase dotted namespaces", () => {
        expect(isValidNamespace("company.team")).toBe(true)
        expect(isValidNamespace("dev")).toBe(true)
    })

    test("rejects uppercase, spaces and separators", () => {
        expect(isValidNamespace("Company.Team")).toBe(false)
        expect(isValidNamespace("company team")).toBe(false)
        expect(isValidNamespace(".company")).toBe(false)
        expect(isValidNamespace("")).toBe(false)
    })

    test("rejects namespaces longer than 150 characters", () => {
        expect(isValidNamespace("a".repeat(150))).toBe(true)
        expect(isValidNamespace("a".repeat(151))).toBe(false)
    })
})
