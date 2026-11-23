import {describe, expect, it} from "vitest"
import {collectKeyEvidence, emptyEvidence, unusedDefinedKeys} from "./usageRules.ts"

const evidenceOf = (...sources: string[]) => sources.reduce((evidence, source) => collectKeyEvidence(source, evidence), emptyEvidence())

describe("collectKeyEvidence", () => {
    it("keeps both keys of a ternary inside a bound attribute", () => {
        const evidence = evidenceOf("<i18n-t :keypath=\"added ? 'ai.copilot.contextAdded' : 'ai.copilot.contextRemoved'\" />")
        expect(evidence.strings.has("ai.copilot.contextAdded")).toBe(true)
        expect(evidence.strings.has("ai.copilot.contextRemoved")).toBe(true)
    })

    it("reads a dot-terminated string as a namespace completed elsewhere", () => {
        expect(evidenceOf("const KEY_PREFIX = \"errors.problems.\"").namespaces.has("errors.problems")).toBe(true)
    })

    it("turns a template literal into the pattern of the keys it can build, wherever it is assigned", () => {
        const [pattern] = evidenceOf("const key = `empty.${props.type}.title`").patterns
        expect(pattern.test("empty.secrets.title")).toBe(true)
        expect(pattern.test("empty.secrets.content")).toBe(false)
    })

    it("skips a template that opens with an expression, since a key starts with its namespace", () => {
        expect(evidenceOf("const id = `${a}_${b}_${c}`; const url = `${base}/api`").patterns).toEqual([])
    })

    it("inlines a string constant of the same file before reading the template", () => {
        const evidence = evidenceOf("const THEME = \"settings.blocks.theme\"\nnotify(t(`${THEME}.confirmations.${field}`))\nconst whole = `${THEME}.title`")
        expect(evidence.patterns.some((pattern: RegExp) => pattern.test("settings.blocks.theme.confirmations.color_mode"))).toBe(true)
        expect(evidence.strings.has("settings.blocks.theme.title")).toBe(true)
    })
})

describe("unusedDefinedKeys", () => {
    const leaves = ["used", "node.child", "ns.built", "data.held", "tpl.x.title", "held.ns.title", "declared", "dead"]
    const evidence = evidenceOf(
        "t(\"used\"); $t(\"node\")[status]; t(\"ns.\" + code)",
        "const rows = [{labelKey: \"data.held\"}]",
        "const key = `tpl.${type}.title`",
        "const prefix = computed(() => props.a ? \"held.ns\" : \"other\")",
        "// i18n-keys: declared\n<h2>{{ $t(elementType) }}</h2>",
    )

    it("reports only the keys no evidence reaches", () => {
        expect(unusedDefinedKeys(leaves, evidence)).toEqual(["dead"])
    })

    it("treats quoted strings and patterns as optional, so the caller can ask for the stricter list", () => {
        const direct = {...evidence, strings: new Set<string>(), patterns: []}
        expect(unusedDefinedKeys(leaves, direct)).toEqual(["data.held", "tpl.x.title", "held.ns.title", "dead"])
    })
})
