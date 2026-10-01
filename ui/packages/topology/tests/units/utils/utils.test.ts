import {describe, expect, test} from "vitest"
import {flowableName, shortPluginType} from "../../../src/utils/utils"

describe("flowableName", () => {
    test("keeps a single-word flowable as is", () => {
        expect(flowableName("io.kestra.plugin.core.flow.Parallel")).toBe("Parallel")
        expect(flowableName("io.kestra.plugin.core.flow.Dag")).toBe("Dag")
    })

    test("splits a camel-cased flowable back into words", () => {
        expect(flowableName("io.kestra.plugin.core.flow.EachSequential")).toBe("Each Sequential")
        expect(flowableName("io.kestra.plugin.core.flow.AllowFailure")).toBe("Allow Failure")
        expect(flowableName("io.kestra.plugin.core.flow.ForEachItem")).toBe("For Each Item")
    })

    test("returns an empty string for a missing type", () => {
        expect(flowableName(undefined)).toBe("")
    })

    test("leaves the full short type untouched — it is still the tooltip", () => {
        expect(shortPluginType("io.kestra.plugin.core.flow.EachSequential")).toBe("core.flow.EachSequential")
    })
})
