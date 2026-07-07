import {beforeEach, describe, expect, it, vi} from "vitest"
import {getTheme, getSelectedTheme, switchTheme, type SelectedTheme, flatten, executionVars} from "../../../src/utils/utils"

function mockSystemPrefersDark(prefersDark: boolean) {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
        matches: prefersDark,
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
    })) as any
}

describe("theme utils", () => {
    beforeEach(() => {
        localStorage.clear()
        document.documentElement.className = ""
        mockSystemPrefersDark(false)
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

    it("flattens arrays with index keys and keeps nulls", () => {
        expect(flatten({list: ["a", "b"], empty: null}))
            .toEqual({"list.0": "a", "list.1": "b", empty: null})
    })
})

describe("executionVars()", () => {
    it("returns one row per flattened output", () => {
        const rows = executionVars({values: {greeting: "hello"}})
        expect(rows).toEqual([{key: "values.greeting", value: "hello"}])
    })

    it("returns an empty list when data is undefined", () => {
        expect(executionVars(undefined as any)).toEqual([])
    })
})
