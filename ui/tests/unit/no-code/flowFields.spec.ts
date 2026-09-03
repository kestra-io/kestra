import {describe, it, expect} from "vitest"
import {getFlowFields} from "../../../src/components/no-code/blocks/flowFields"

describe("getFlowFields", () => {
    it("excludes quotas on OSS, where the executor rejects it at runtime", () => {
        expect(getFlowFields("OSS")).not.toContain("quotas")
    })

    it("includes quotas on EE", () => {
        expect(getFlowFields("EE")).toContain("quotas")
    })

    it("includes quotas when the edition is unknown", () => {
        expect(getFlowFields(undefined)).toContain("quotas")
    })
})
