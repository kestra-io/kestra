import {describe, expect, it} from "vitest"
import {translationNamespaceUsages, undefinedNamespaceUsages} from "./usageRules.mjs"

describe("translationNamespaceUsages", () => {
    it("reads the namespace of a concatenated prefix", () => {
        expect(translationNamespaceUsages("$t(\"crud.type.\" + row.type)")).toEqual([{namespace: "crud.type", line: 1}])
    })

    it("reads the namespace of a template literal prefix", () => {
        expect(translationNamespaceUsages("t(`ai.copilot.error.${error}`)")).toEqual([{namespace: "ai.copilot.error", line: 1}])
    })

    it("keeps only the part before the last dot when the runtime part completes a segment", () => {
        expect(translationNamespaceUsages("$t(`source_search.type_${type}`)")).toEqual([{namespace: "source_search", line: 1}])
    })

    it("skips a prefix that names no namespace", () => {
        expect(translationNamespaceUsages("t(\"open in \" + item)")).toEqual([])
        expect(translationNamespaceUsages("t(`triggers_add_filter_${group}`)")).toEqual([])
    })

    it("ignores complete literal keys and non-translation calls", () => {
        expect(translationNamespaceUsages("t(\"flows.create\"); format(\"a.\" + b); t(`demos.tests.title`)")).toEqual([])
    })

    it("reports the line of each usage", () => {
        const source = "const a = 1\nconst b = t(\"errors.\" + code + \".title\")\n"
        expect(translationNamespaceUsages(source)).toEqual([{namespace: "errors", line: 2}])
    })
})

describe("undefinedNamespaceUsages", () => {
    it("reports namespaces that exist in no key set", () => {
        const usages = {"a.vue": [{namespace: "crud.type", line: 3}, {namespace: "gone", line: 9}]}
        expect(undefinedNamespaceUsages(usages, new Set(["crud", "crud.type", "crud.type.CREATE"])))
            .toEqual([{file: "a.vue", line: 9, namespace: "gone"}])
    })
})
