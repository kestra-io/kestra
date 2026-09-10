import {describe, it, expect} from "vitest"
import {getFlowFields} from "../../../src/components/no-code/blocks/flowFields"

describe("getFlowFields", () => {
    for (const key of ["quotas", "policyRefs"]) {
        it(`excludes ${key} on OSS, where it either crash-loops the server or is silently ignored`, () => {
            expect(getFlowFields("OSS")).not.toContain(key)
        })

        it(`includes ${key} on EE`, () => {
            expect(getFlowFields("EE")).toContain(key)
        })

        it(`includes ${key} when the edition is unknown`, () => {
            expect(getFlowFields(undefined)).toContain(key)
        })
    }
})
