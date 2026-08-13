import {describe, expect, it} from "vitest"
import {
    buildTermHighlightHtml,
    buildTermHighlightSegments,
    splitQueryTerms,
    buildHighlightHtml,
    buildHighlightSegments,
    buildPathSegments,
    crossSearchResultKey,
    groupByNamespace,
    matchesLiteral,
    searchViewState,
    type SearchViewStateInput,
} from "../../../src/utils/crossResourceSearch"

describe("matchesLiteral", () => {
    it("matches case-insensitively by default", () => {
        expect(matchesLiteral("AWS_US_EAST_1_ACCESS_KEY", "us_east_1")).toBe(true)
    })

    it("does not match separators loosely (literal, no normalization)", () => {
        expect(matchesLiteral("AWS_US_EAST_1_ACCESS_KEY", "us-east-1")).toBe(false)
    })

    it("respects case sensitivity when requested", () => {
        expect(matchesLiteral("us-east-1", "US-EAST-1", true)).toBe(false)
        expect(matchesLiteral("US-EAST-1", "US-EAST-1", true)).toBe(true)
    })

    it("returns false for an empty query", () => {
        expect(matchesLiteral("anything", "")).toBe(false)
    })
})

describe("buildHighlightSegments", () => {
    it("returns the full text unmatched when the query is empty", () => {
        expect(buildHighlightSegments("hello", "")).toEqual([{text: "hello", matched: false}])
    })

    it("returns the full text unmatched when there is no occurrence", () => {
        expect(buildHighlightSegments("hello", "xyz")).toEqual([{text: "hello", matched: false}])
    })

    it("splits a single occurrence into before/match/after segments", () => {
        expect(buildHighlightSegments("landing-bucket-us-east-1", "us-east-1")).toEqual([
            {text: "landing-bucket-", matched: false},
            {text: "us-east-1", matched: true},
        ])
    })

    it("highlights every non-overlapping occurrence", () => {
        expect(buildHighlightSegments("dup and dup", "dup")).toEqual([
            {text: "dup", matched: true},
            {text: " and ", matched: false},
            {text: "dup", matched: true},
        ])
    })

    it("matches case-insensitively by default while preserving original casing", () => {
        expect(buildHighlightSegments("AWS-US-EAST-1-KEY", "us-east-1")).toEqual([
            {text: "AWS-", matched: false},
            {text: "US-EAST-1", matched: true},
            {text: "-KEY", matched: false},
        ])
    })

    it("does not match when case-sensitive is requested and casing differs", () => {
        expect(buildHighlightSegments("US-EAST-1", "us-east-1", true)).toEqual([{text: "US-EAST-1", matched: false}])
    })
})

describe("buildPathSegments", () => {
    it("dims directory segments but not the filename", () => {
        const segments = buildPathSegments("scripts/us-east-1/extract.py", "us-east-1")
        expect(segments).toEqual([
            {text: "scripts/", matched: false, dim: true},
            {text: "us-east-1", matched: true, dim: true},
            {text: "/", matched: false, dim: true},
            {text: "extract.py", matched: false, dim: false},
        ])
    })

    it("does not dim anything for a path with no directory", () => {
        expect(buildPathSegments("us-east-1.yaml", "us-east-1")).toEqual([
            {text: "us-east-1", matched: true, dim: false},
            {text: ".yaml", matched: false, dim: false},
        ])
    })

    it("highlights a match inside the filename even when directories are present", () => {
        const segments = buildPathSegments("configs/us-east-1.yaml", "us-east-1")
        expect(segments).toEqual([
            {text: "configs/", matched: false, dim: true},
            {text: "us-east-1", matched: true, dim: false},
            {text: ".yaml", matched: false, dim: false},
        ])
    })
})

describe("crossSearchResultKey", () => {
    it("builds a distinct key per type", () => {
        expect(crossSearchResultKey({type: "flows", namespace: "ns", id: "flow", line: 4, column: 8})).toBe("flows:ns.flow#4:8")
        expect(crossSearchResultKey({type: "files", namespace: "ns", path: "scripts/a.py"})).toBe("files:ns#scripts/a.py")
        expect(crossSearchResultKey({type: "kv", namespace: "ns", key: "my-key"})).toBe("kv:ns#my-key")
        expect(crossSearchResultKey({type: "secrets", namespace: "ns", key: "my-secret"})).toBe("secrets:ns#my-secret")
    })
})

describe("groupByNamespace", () => {
    it("groups entries by namespace while preserving first-seen order", () => {
        const entries = [
            {namespace: "b", key: "b1"},
            {namespace: "a", key: "a1"},
            {namespace: "b", key: "b2"},
        ]

        const groups = groupByNamespace(entries, (entry) => entry.namespace, (entry) => entry.key)

        expect(groups).toEqual([
            {namespace: "b", matches: ["b1", "b2"]},
            {namespace: "a", matches: ["a1"]},
        ])
    })

    it("returns an empty array for no entries", () => {
        expect(groupByNamespace([], (entry: {namespace: string}) => entry.namespace, (entry) => entry)).toEqual([])
    })
})

describe("buildHighlightHtml", () => {
    it("wraps the matched segment in a mark tag", () => {
        expect(buildHighlightHtml("landing-bucket-us-east-1", "us-east-1")).toBe("landing-bucket-<mark>us-east-1</mark>")
    })

    it("escapes html in both matched and unmatched segments", () => {
        expect(buildHighlightHtml("<script>us-east-1</script>", "us-east-1")).toBe("&lt;script&gt;<mark>us-east-1</mark>&lt;/script&gt;")
    })

    it("returns the escaped text unmarked when there is no query", () => {
        expect(buildHighlightHtml("<b>hello</b>", "")).toBe("&lt;b&gt;hello&lt;/b&gt;")
    })
})

describe("term highlighting (KV and namespace files)", () => {
    it("splits a query into terms on any non-alphanumeric separator", () => {
        expect(splitQueryTerms("landing.bucket")).toEqual(["landing", "bucket"])
        expect(splitQueryTerms("us-east-1")).toEqual(["us", "east", "1"])
        expect(splitQueryTerms("  spaced   out ")).toEqual(["spaced", "out"])
    })

    it("marks a row the server matched on terms, which whole-query highlighting leaves unmarked", () => {
        const key = "landing-bucket-us-east-1"

        // What the user sees today: the literal query never occurs, so nothing is highlighted.
        expect(buildTermHighlightHtml(key, "landing.bucket"))
            .toBe("<mark>landing</mark>-<mark>bucket</mark>-us-east-1")
    })

    it("merges overlapping and adjacent term matches into one segment", () => {
        expect(buildTermHighlightSegments("abcdef", "abc bcd")).toEqual([
            {text: "abcd", matched: true},
            {text: "ef", matched: false},
        ])
    })

    it("highlights every occurrence of each term", () => {
        expect(buildTermHighlightHtml("log-a-log", "log")).toBe("<mark>log</mark>-a-<mark>log</mark>")
    })

    it("honours case sensitivity", () => {
        expect(buildTermHighlightHtml("LANDING_BUCKET", "landing")).toBe("<mark>LANDING</mark>_BUCKET")
        expect(buildTermHighlightHtml("LANDING_BUCKET", "landing", true)).toBe("LANDING_BUCKET")
    })

    it("escapes the text it marks", () => {
        expect(buildTermHighlightHtml("<b>x</b>", "b")).toBe("&lt;<mark>b</mark>&gt;x&lt;/<mark>b</mark>&gt;")
    })

    it("leaves text untouched when the query has no terms", () => {
        expect(buildTermHighlightHtml("anything", "---")).toBe("anything")
    })
})

describe("searchViewState", () => {
    const state = (overrides: Partial<SearchViewStateInput> = {}) => searchViewState({
        hasQuery: true,
        loadInit: true,
        searchPending: false,
        anyCounting: false,
        matchCount: 0,
        ...overrides,
    })

    it("shows the initial prompt when there is no query", () => {
        expect(state({hasQuery: false})).toBe("initial")
        expect(state({hasQuery: false, searchPending: true})).toBe("initial")
    })

    it("loads instead of claiming no matches while a search is only scheduled", () => {
        // The debounce window: a keystroke landed, no request is out yet, so every status is
        // still idle and every count still zero.
        expect(state({searchPending: true})).toBe("loading")
    })

    it("loads while a type is counting and nothing has arrived yet", () => {
        expect(state({anyCounting: true})).toBe("loading")
    })

    it("loads while the URL restore navigation is still in flight", () => {
        expect(state({loadInit: false})).toBe("loading")
    })

    it("reports empty only once the search has settled with no matches", () => {
        expect(state()).toBe("empty")
    })

    it("shows partial results as they stream in rather than a skeleton", () => {
        // Namespace files fan out one namespace at a time; the first hits must not be hidden
        // behind a loading state for the rest of the fan-out.
        expect(state({anyCounting: true, matchCount: 3})).toBe("results")
        expect(state({searchPending: true, matchCount: 3})).toBe("results")
    })

    it("shows results for a settled search with matches", () => {
        expect(state({matchCount: 10})).toBe("results")
    })
})
