import {describe, expect, it} from "vitest"
import {latestRequest} from "./latestRequest"

describe("latestRequest", () => {
    it("keeps only the newest request current, whatever order the responses come back in", () => {
        const start = latestRequest()

        const first = start()
        const second = start()

        expect(second()).toBe(true)
        expect(first()).toBe(false)
    })

    it("tracks each sequence on its own", () => {
        const startSearch = latestRequest()
        const startLoad = latestRequest()

        const search = startSearch()
        startLoad()

        expect(search()).toBe(true)
    })
})
