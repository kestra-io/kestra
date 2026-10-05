import {afterAll, afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {getTheme, getSelectedTheme, switchTheme, type SelectedTheme, flatten, executionVars, getDateGrouping, downloadUrl, splitFirst, getParentNamespaces, isFile, isIon} from "./utils"

function mockSystemPrefersDark(prefersDark: boolean) {
    vi.stubGlobal("matchMedia", vi.fn().mockImplementation((query: string) => ({
        matches: prefersDark,
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
    })))
}

describe("downloadUrl()", () => {
    afterEach(() => vi.restoreAllMocks())

    // https://github.com/kestra-io/kestra/issues/17322
    it("does not set a target attribute", () => {
        const createElementSpy = vi.spyOn(document, "createElement")

        downloadUrl("blob:http://localhost/fake", "flow.yaml")

        const link = createElementSpy.mock.results[0]?.value as HTMLAnchorElement
        expect(link.getAttribute("download")).toBe("flow.yaml")
        expect(link.getAttribute("target")).toBeNull()
    })
})

describe("theme utils", () => {
    beforeEach(() => {
        localStorage.clear()
        document.documentElement.className = ""
        mockSystemPrefersDark(false)
    })

    afterAll(() => {
        localStorage.clear()
        document.documentElement.className = ""
        vi.unstubAllGlobals()
    })

    describe("getTheme()", () => {
        it("collapses dark-2 to dark so consumers branching on 'dark' render dark", () => {
            localStorage.setItem("theme", "dark-2")
            expect(getTheme()).toBe("dark")
        })

        it("returns the concrete value for dark and light", () => {
            localStorage.setItem("theme", "dark")
            expect(getTheme()).toBe("dark")
            localStorage.setItem("theme", "light")
            expect(getTheme()).toBe("light")
        })

        it("resolves syncWithSystem via prefers-color-scheme", () => {
            localStorage.setItem("theme", "syncWithSystem")
            mockSystemPrefersDark(true)
            expect(getTheme()).toBe("dark")
            mockSystemPrefersDark(false)
            expect(getTheme()).toBe("light")
        })
    })

    describe("getSelectedTheme()", () => {
        it("preserves the raw selection (dark-2) for the settings picker", () => {
            localStorage.setItem("theme", "dark-2")
            expect(getSelectedTheme()).toBe("dark-2")
        })

        it("defaults to syncWithSystem when nothing is stored", () => {
            expect(getSelectedTheme()).toBe("syncWithSystem")
        })
    })

    describe("switchTheme()", () => {
        const newStore = () => ({theme: undefined} as unknown as {theme: SelectedTheme})

        it("layers both dark and dark-2 classes for the dark-2 theme", () => {
            switchTheme(newStore(), "dark-2")
            const cls = document.documentElement.classList
            expect(cls.contains("dark")).toBe(true)
            expect(cls.contains("dark-2")).toBe(true)
        })

        it("clears the dark-2 class when switching back to light", () => {
            switchTheme(newStore(), "dark-2")
            switchTheme(newStore(), "light")
            const cls = document.documentElement.classList
            expect(cls.contains("dark-2")).toBe(false)
            expect(cls.contains("dark")).toBe(false)
            expect(cls.contains("light")).toBe(true)
        })

        it("stores the raw selection (not the effective value) in localStorage", () => {
            switchTheme(newStore(), "dark-2")
            expect(localStorage.getItem("theme")).toBe("dark-2")
            expect(getSelectedTheme()).toBe("dark-2")
        })
    })
})

describe("flatten()", () => {
    it("keeps flat keys as-is", () => {
        expect(flatten({a: 1, b: "x"})).toEqual({a: 1, b: "x"})
    })

    it("flattens nested objects to dotted keys", () => {
        expect(flatten({values: {greeting: "hello", count: "42"}, uri: "kestra:///x"}))
            .toEqual({"values.greeting": "hello", "values.count": "42", uri: "kestra:///x"})
    })

    // An empty output used to vanish from the Outputs view: recursion found no leaves and
    // contributed nothing, so the user could not tell an empty value from a missing one.
    it("keeps an empty object as its own value instead of dropping the key", () => {
        expect(flatten({data: "Code finished", outputFiles: {}}))
            .toEqual({data: "Code finished", outputFiles: {}})
    })

    it("keeps an empty array as its own value instead of dropping the key", () => {
        expect(flatten({data: "x", outputFiles: []})).toEqual({data: "x", outputFiles: []})
    })

    it("keeps a nested empty object at its dotted path", () => {
        expect(flatten({a: {b: {}}})).toEqual({"a.b": {}})
    })

    it("still flattens a top-level empty object to an empty result", () => {
        expect(flatten({})).toEqual({})
    })

    it("flattens arrays with index keys and keeps nulls", () => {
        expect(flatten({list: ["a", "b"], empty: null}))
            .toEqual({"list.0": "a", "list.1": "b", empty: null})
    })
})

describe("getDateGrouping()", () => {
    it("returns a date-only day grouping when no dates and no time range are provided", () => {
        expect(getDateGrouping(undefined, undefined, undefined)).toEqual({format: "YYYY-MM-DD", unit: "day"})
    })

    it("returns a month grouping for ranges over a year", () => {
        expect(getDateGrouping(undefined, undefined, "P400D")).toEqual({format: "YYYY-MM", unit: "month"})
    })

    it("returns a week grouping for ranges over 180 days", () => {
        expect(getDateGrouping(undefined, undefined, "P200D")).toEqual({format: "YYYY-[W]ww", unit: "week"})
    })

    it("returns a day grouping for ranges over a day", () => {
        expect(getDateGrouping(undefined, undefined, "P7D")).toEqual({format: "YYYY-MM-DD", unit: "day"})
    })

    it("returns an hour grouping, date and hour separated with a space, for ranges over an hour", () => {
        expect(getDateGrouping(undefined, undefined, "PT24H")).toEqual({format: "YYYY-MM-DD HH:00", unit: "hour"})
    })

    it("returns a minute grouping, date and time separated with a space, for ranges up to an hour", () => {
        expect(getDateGrouping(undefined, undefined, "PT30M")).toEqual({format: "YYYY-MM-DD HH:mm", unit: "minute"})
    })

    it("derives the duration from start and end dates when no time range is provided", () => {
        expect(getDateGrouping("2026-08-17T00:00:00Z", "2026-08-17T12:00:00Z", undefined)).toEqual({format: "YYYY-MM-DD HH:00", unit: "hour"})
    })
})

describe("executionVars()", () => {
    it("returns one row per flattened output", () => {
        const rows = executionVars({values: {greeting: "hello"}})
        expect(rows).toEqual([{key: "values.greeting", value: "hello"}])
    })

    it("returns an empty list when data is undefined", () => {
        expect(executionVars(undefined as unknown as Record<string, unknown>)).toEqual([])
    })
})
describe("splitFirst()", () => {
    it("drops the first segment and rejoins the rest", () => {
        expect(splitFirst("a/b/c", "/")).toBe("b/c")
        expect(splitFirst("namespace/flow/task/output", "/")).toBe("flow/task/output")
        expect(splitFirst("a:b:c", ":")).toBe("b:c")
    })

    it("returns an empty string when the separator is absent", () => {
        expect(splitFirst("abc", "/")).toBe("")
        expect(splitFirst("no_separator", ":")).toBe("")
        expect(splitFirst("", "/")).toBe("")
    })

    it("returns an empty string when there is only one segment before the separator", () => {
        expect(splitFirst("a/", "/")).toBe("")
    })
})

describe("getParentNamespaces()", () => {
    it("returns every ancestor of a nested namespace, in order", () => {
        expect(getParentNamespaces("a.b.c")).toEqual(["a", "a.b", "a.b.c"])
        expect(getParentNamespaces("company.team.project")).toEqual([
            "company",
            "company.team",
            "company.team.project",
        ])
    })

    it("returns nothing extra for a root-level namespace", () => {
        expect(getParentNamespaces("company")).toEqual(["company"])
        expect(getParentNamespaces("root")).toEqual(["root"])
    })

    it("returns an empty array for empty or falsy namespace", () => {
        expect(getParentNamespaces("")).toEqual([])
    })
})

describe("isFile()", () => {
    it("returns true for an internal-storage file URI", () => {
        expect(isFile("kestra:///company/flow/execution/file.txt")).toBe(true)
        expect(isFile("kestra:///output.ion")).toBe(true)
        expect(isFile("nsfile:///data/input.csv")).toBe(true)
    })

    it("returns false for an ordinary string or non-file values", () => {
        expect(isFile("ordinary-string")).toBe(false)
        expect(isFile("https://example.com/file.txt")).toBe(false)
        expect(isFile("/local/path/file.txt")).toBe(false)
        expect(isFile("")).toBe(false)
        expect(isFile(null)).toBe(false)
        expect(isFile(undefined)).toBe(false)
        expect(isFile(123)).toBe(false)
        expect(isFile({})).toBe(false)
    })
})

describe("isIon()", () => {
    it("returns true only for a file URI ending in .ion, case-insensitively", () => {
        expect(isIon("kestra:///data/output.ion")).toBe(true)
        expect(isIon("kestra:///data/OUTPUT.ION")).toBe(true)
        expect(isIon("kestra:///data/records.Ion")).toBe(true)
        expect(isIon("nsfile:///data/output.ion")).toBe(true)
    })

    it("returns false for a file URI with a different extension", () => {
        expect(isIon("kestra:///data/output.csv")).toBe(false)
        expect(isIon("kestra:///data/output.json")).toBe(false)
        expect(isIon("kestra:///data/output.txt")).toBe(false)
    })

    it("returns false for a non-file value that merely ends in .ion", () => {
        expect(isIon("regular-text.ion")).toBe(false)
        expect(isIon("https://example.com/data.ion")).toBe(false)
        expect(isIon("/local/path/test.ion")).toBe(false)
        expect(isIon(".ion")).toBe(false)
        expect(isIon(null)).toBe(false)
        expect(isIon(undefined)).toBe(false)
        expect(isIon({uri: "kestra:///data/output.ion"})).toBe(false)
    })
})
