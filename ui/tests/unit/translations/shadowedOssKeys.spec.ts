import {describe, expect, it} from "vitest"
// @ts-expect-error - the rules module is plain JS so the PR gate can run it before any install
import {shadowedOssKeys} from "../../../scripts/translations/translationRules.mjs"

describe("shadowedOssKeys", () => {
    it("reports nothing when EE and OSS keys are disjoint", () => {
        expect(shadowedOssKeys(["apps.create", "iam.title"], ["flows", "executions.list"])).toEqual([])
    })

    it("reports a leaf defined on both sides", () => {
        expect(shadowedOssKeys(["cancel"], ["cancel", "save"])).toEqual([
            {key: "cancel", ossKey: "cancel", kind: "duplicate"},
        ])
    })

    // kestra-io/kestra-ee#10001: EE's `concurrency.*` section turned OSS's `concurrency` message
    // into an object, so the flow tab rendered the key path instead of "Concurrency".
    it("reports an EE namespace nested under an OSS message", () => {
        expect(shadowedOssKeys(["concurrency.section", "concurrency.behaviors.QUEUE"], ["concurrency"])).toEqual([
            {key: "concurrency.section", ossKey: "concurrency", kind: "nested-under-oss-leaf"},
            {key: "concurrency.behaviors.QUEUE", ossKey: "concurrency", kind: "nested-under-oss-leaf"},
        ])
    })

    it("attributes a nested key to its nearest shadowed ancestor", () => {
        expect(shadowedOssKeys(["a.b.c"], ["a.b", "a.b.c.d"])).toEqual([
            {key: "a.b.c", ossKey: "a.b", kind: "nested-under-oss-leaf"},
        ])
    })

    it("reports an EE message placed on top of an OSS namespace", () => {
        expect(shadowedOssKeys(["apps"], ["apps.create", "apps.delete"])).toEqual([
            {key: "apps", ossKey: "apps", kind: "replaces-oss-namespace"},
        ])
    })

    it("does not confuse a shared prefix with a shared path", () => {
        expect(shadowedOssKeys(["concurrencySettings.section"], ["concurrency"])).toEqual([])
    })
})
