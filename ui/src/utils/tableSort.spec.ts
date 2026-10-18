import {describe, expect, it} from "vitest"
import {sortFromQuery, type TableSort} from "./tableSort"

const FALLBACK: TableSort = {prop: "id", order: "ascending"}

describe("sortFromQuery", () => {
    it("keeps the fallback when the URL has no sort", () => {
        expect(sortFromQuery(undefined, FALLBACK)).toEqual(FALLBACK)
    })

    it("reads the column and direction from the URL", () => {
        expect(sortFromQuery("namespace:desc", FALLBACK)).toEqual({prop: "namespace", order: "descending"})
        expect(sortFromQuery("state.startDate:asc", FALLBACK)).toEqual({prop: "state.startDate", order: "ascending"})
    })

    // The API sorts ascending only for the exact direction "asc", so anything else lists the rows descending.
    it("reads any direction other than asc as descending", () => {
        expect(sortFromQuery("key:ASC", FALLBACK)).toEqual({prop: "key", order: "descending"})
    })

    // The API answers each of these with a 422, so there is no row order for the arrow to agree with.
    it.each(["key", "key:", ":desc", "key:desc:asc"])("keeps the fallback for the malformed sort %s", (sort) => {
        expect(sortFromQuery(sort, FALLBACK)).toEqual(FALLBACK)
    })

    it("keeps the fallback when the URL repeats the sort parameter", () => {
        expect(sortFromQuery(["key:desc", "namespace:asc"], FALLBACK)).toEqual(FALLBACK)
    })
})
