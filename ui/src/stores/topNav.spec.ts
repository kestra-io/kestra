import {beforeEach, describe, expect, it} from "vitest"
import {computed} from "vue"
import {createPinia, setActivePinia} from "pinia"
import type {KsBreadcrumbItem} from "@kestra-io/design-system"
import {useTopNavStore} from "./topNav"

describe("topNav store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("defaults the title to an empty string and the breadcrumb to an empty list", () => {
        const store = useTopNavStore()

        expect(store.title).toBe("")
        expect(store.breadcrumb).toEqual([])
    })

    it("ensures setting the title and breadcrumb is observable by a reader", () => {
        const store = useTopNavStore()
        const items: KsBreadcrumbItem[] = [
            {label: "Home", href: "/"},
            {label: "Flows", href: "/flows"},
        ]

        store.title = "Flow Overview"
        store.breadcrumb = items

        expect(store.title).toBe("Flow Overview")
        expect(store.breadcrumb).toEqual(items)
    })

    it("defaults bookmarkLabel and description to undefined and allows them to be set and cleared", () => {
        const store = useTopNavStore()

        expect(store.bookmarkLabel).toBeUndefined()
        expect(store.description).toBeUndefined()

        // Setting values
        store.bookmarkLabel = "Favorite Flow"
        store.description = "Runs nightly backups"
        expect(store.bookmarkLabel).toBe("Favorite Flow")
        expect(store.description).toBe("Runs nightly backups")

        // Clearing values
        store.bookmarkLabel = undefined
        store.description = undefined
        expect(store.bookmarkLabel).toBeUndefined()
        expect(store.description).toBeUndefined()
    })

    it("updates reactive readers when the breadcrumb shallowRef array is replaced", () => {
        const store = useTopNavStore()
        const observedBreadcrumb = computed(() => store.breadcrumb)

        expect(observedBreadcrumb.value).toEqual([])

        const newBreadcrumb: KsBreadcrumbItem[] = [
            {label: "Namespaces", href: "/namespaces"},
            {label: "company.team", href: "/flows/company.team"},
        ]

        store.breadcrumb = newBreadcrumb
        expect(observedBreadcrumb.value).toBe(newBreadcrumb)
        expect(observedBreadcrumb.value).toHaveLength(2)
        expect(observedBreadcrumb.value[0].label).toBe("Namespaces")
    })

    it("ensures two readers see the same shared state", () => {
        const reader1 = useTopNavStore()
        const reader2 = useTopNavStore()

        reader1.title = "Executions"
        reader1.description = "List of current flow executions"
        reader1.bookmarkLabel = "Executions Bookmark"
        reader1.beta = true

        expect(reader2.title).toBe("Executions")
        expect(reader2.description).toBe("List of current flow executions")
        expect(reader2.bookmarkLabel).toBe("Executions Bookmark")
        expect(reader2.beta).toBe(true)
    })
})
