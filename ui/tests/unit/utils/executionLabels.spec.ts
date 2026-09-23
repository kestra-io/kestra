import {describe, expect, it} from "vitest"
import {
    buildExecutionLabelStrings,
    hasForbiddenUserSystemLabels,
    hasInvalidLabelKeys,
    isForbiddenUserSystemLabel,
    isValidLabelKey,
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

    it.each([
        "env", "env-name", "env_name", "env.name", "env2", "system.correlationId",
    ])("shouldAcceptValidLabelKey %s", (key) => {
        expect(isValidLabelKey(key)).toBe(true)
    })

    it.each([
        "ENV", "Env", "2env", "-env", "_env", "env name", "env@name", "env:name", "env🙂",
    ])("shouldRejectInvalidLabelKey %s", (key) => {
        expect(isValidLabelKey(key)).toBe(false)
    })

    it("shouldDetectInvalidKeysInAList", () => {
        expect(hasInvalidLabelKeys([{key: "env", value: "prod"}])).toBe(false)
        expect(hasInvalidLabelKeys([{key: "FOO", value: "bar"}])).toBe(true)
        expect(hasInvalidLabelKeys([{key: null, value: null}])).toBe(false)
    })
})
