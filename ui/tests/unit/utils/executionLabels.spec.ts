import {describe, expect, it} from "vitest"
import {
    buildExecutionLabelStrings,
    hasForbiddenUserSystemLabels,
    isForbiddenUserSystemLabel,
} from "../../../src/utils/executionLabels"

describe("executionLabels", () => {
    it("shouldTreatSystemFromAsForbiddenForManualEntry", () => {
        expect(isForbiddenUserSystemLabel("system.from")).toBe(true)
    })

    it("shouldAllowSystemCorrelationId", () => {
        expect(isForbiddenUserSystemLabel("system.correlationId")).toBe(false)
    })

    it("shouldRejectOtherSystemLabels", () => {
        expect(isForbiddenUserSystemLabel("system.username")).toBe(true)
        expect(hasForbiddenUserSystemLabels([{key: "system.from", value: "ui"}])).toBe(true)
        expect(hasForbiddenUserSystemLabels([{key: "system.correlationId", value: "abc"}])).toBe(false)
        expect(hasForbiddenUserSystemLabels([{key: "env", value: "prod"}])).toBe(false)
    })

    it("shouldAppendSystemFromUiWhenMissing", () => {
        expect(buildExecutionLabelStrings([{key: "env", value: "prod"}])).toEqual([
            "env:prod",
            "system.from:ui",
        ])
    })

    it("shouldNotDuplicateSystemFromWhenAlreadyPresent", () => {
        expect(buildExecutionLabelStrings([{key: "system.from", value: "custom"}])).toEqual([
            "system.from:custom",
        ])
    })
})
