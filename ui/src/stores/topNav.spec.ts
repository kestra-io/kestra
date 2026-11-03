import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"

import {useTopNavStore} from "./topNav"

describe("top nav store", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("starts with an empty title and breadcrumb", () => {
        const store = useTopNavStore()

        expect(store.title).toBe("")
        expect(store.breadcrumb).toEqual([])
        expect(store.bookmarkLabel).toBeUndefined()
        expect(store.description).toBeUndefined()
    })

    it("updates the title and replaces the breadcrumb", () => {
        const store = useTopNavStore()
        const breadcrumb = [{label: "Flows"}]

        store.title = "Executions"
        store.breadcrumb = breadcrumb

        expect(store.title).toBe("Executions")
        expect(store.breadcrumb).toBe(breadcrumb)
    })
})
