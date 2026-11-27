import {describe, test, expect} from "vitest"
import {sortFromQuery} from "./tableSort"

const FALLBACK = {prop: "id", order: "ascending"} as const

describe("sortFromQuery", () => {
    test.each([
        ["namespace:asc", {prop: "namespace", order: "ascending"}],
        ["state.startDate:desc", {prop: "state.startDate", order: "descending"}],
        ["updateDate:DESC", {prop: "updateDate", order: "descending"}],
    ])("follows the URL sort %s", (sort, expected) => {
        expect(sortFromQuery(sort, FALLBACK)).toEqual(expected)
    })

    test.each([
        ["no sort", undefined],
        ["a sort with no direction", "namespace"],
        ["a sort with no column", ":desc"],
        ["a sort with extra parts", "a:b:desc"],
        ["a repeated sort parameter", ["namespace:desc", "id:asc"]],
    ])("falls back to the page default for %s", (_label, sort) => {
        expect(sortFromQuery(sort, FALLBACK)).toEqual(FALLBACK)
    })
})
