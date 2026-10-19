import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {nextTick, watch} from "vue"

import {useTopNavStore} from "./topNav"

describe("top nav store", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("starts with an empty title and breadcrumb", () => {
        const store = useTopNavStore()

        expect(store.title).toBe("")
        expect(store.breadcrumb).toEqual([])
    })

    it("updates the title and replaces the breadcrumb", () => {
        const store = useTopNavStore()
        const breadcrumb = [{label: "Flows"}]

        store.title = "Executions"
        store.breadcrumb = breadcrumb

        expect(store.title).toBe("Executions")
        expect(store.breadcrumb).toBe(breadcrumb)
    })

    it("starts bookmark label and description as undefined and allows them to be cleared", () => {
        const store = useTopNavStore()

        expect(store.bookmarkLabel).toBeUndefined()
        expect(store.description).toBeUndefined()

        store.bookmarkLabel = "My flows"
        store.description = "Flow executions"

        expect(store.bookmarkLabel).toBe("My flows")
        expect(store.description).toBe("Flow executions")

        store.bookmarkLabel = undefined
        store.description = undefined

        expect(store.bookmarkLabel).toBeUndefined()
        expect(store.description).toBeUndefined()
    })

    it("notifies watchers when the breadcrumb is replaced", async () => {
        const store = useTopNavStore()
        const breadcrumb = [{label: "Flows"}]
        const observed: Array<typeof breadcrumb> = []
        const stop = watch(
            () => store.breadcrumb,
            value => observed.push(value),
        )

        try {
            store.breadcrumb = breadcrumb
            await nextTick()

            expect(observed).toEqual([breadcrumb])
        } finally {
            stop()
        }
    })

    it("shares updated state across multiple store readers", () => {
        const firstReader = useTopNavStore()
        const secondReader = useTopNavStore()
        const breadcrumb = [{label: "Flows"}]

        firstReader.title = "Executions"
        firstReader.breadcrumb = breadcrumb
        firstReader.bookmarkLabel = "My flows"
        firstReader.description = "Flow executions"

        expect(secondReader.title).toBe("Executions")
        expect(secondReader.breadcrumb).toBe(breadcrumb)
        expect(secondReader.bookmarkLabel).toBe("My flows")
        expect(secondReader.description).toBe("Flow executions")
    })
})
