import {afterEach, describe, test, expect, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {nextTick} from "vue"
import {createRouter, createMemoryHistory} from "vue-router"
import KsBreadcrumbColumn from "./KsBreadcrumbColumn.vue"
import locales from "./KsBreadcrumb.locale"
import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "./types"
import {i18nMount} from "../../../../tests/units/i18nMount"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/:path(.*)*", component: {template: "<div/>"}}],
})

const globalConfig = {plugins: [router]}

function createDeferredLoader<T = KsBreadcrumbItem[]>(result: T) {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((res) => {
        resolve = res
    })
    const loader: KsBreadcrumbLoader = () => promise as unknown as Promise<KsBreadcrumbItem[]>
    return {
        loader,
        resolve: () => resolve(result),
    }
}

// A row opens its fly-out once the pointer has rested on it, not while it only crosses it.
const hover = async (row: {trigger: (event: string) => Promise<void>}) => {
    await row.trigger("mouseenter")
    await new Promise((resolve) => setTimeout(resolve, 170))
    await flushPromises()
}

describe("KsBreadcrumbColumn", () => {
    afterEach(() => {
        document.body.innerHTML = ""
        vi.restoreAllMocks()
    })

    test("1. Entries precedence: When both entries and load are supplied, render entries and not loader results", async () => {
        const directEntries: KsBreadcrumbItem[] = [
            {label: "Direct 1", link: "/direct-1"},
            {label: "Direct 2", link: "/direct-2"},
        ]
        const loaderEntries: KsBreadcrumbItem[] = [
            {label: "Loader 1", link: "/loader-1"},
            {label: "Loader 2", link: "/loader-2"},
        ]
        const load = vi.fn().mockResolvedValue(loaderEntries)

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                entries: directEntries,
                load,
            },
            global: globalConfig,
        })
        await flushPromises()

        const renderedEntries = wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")
        const renderedLabels = renderedEntries.map((el) => el.find(".label").text())

        expect(renderedLabels).toEqual(["Direct 1", "Direct 2"])
    })

    test("2. Async loading: Pass an asynchronous load function and verify items appear after resolution", async () => {
        const deferred = createDeferredLoader([
            {label: "Async A", link: "/async-a"},
            {label: "Async B", link: "/async-b"},
        ])

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                load: deferred.loader,
            },
            global: globalConfig,
        })

        expect(wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")).toHaveLength(0)

        deferred.resolve()
        await flushPromises()

        const renderedEntries = wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")
        const renderedLabels = renderedEntries.map((el) => el.find(".label").text())
        expect(renderedLabels).toEqual(["Async A", "Async B"])
    })

    test("3. Clear previous rows: Previous rows disappear when load changes to a second pending loader", async () => {
        const deferred1 = createDeferredLoader([
            {label: "Old 1", link: "/old-1"},
            {label: "Old 2", link: "/old-2"},
        ])
        const deferred2 = createDeferredLoader([
            {label: "New 1", link: "/new-1"},
        ])

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                load: deferred1.loader,
            },
            global: globalConfig,
        })

        deferred1.resolve()
        await flushPromises()

        expect(wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")).toHaveLength(2)

        await wrapper.setProps({load: deferred2.loader})
        await nextTick()

        // Before deferred2 resolves, previous rows must already be cleared
        expect(wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")).toHaveLength(0)

        deferred2.resolve()
        await flushPromises()

        expect(wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")).toHaveLength(1)
        expect(wrapper.find(".label").text()).toBe("New 1")
    })

    test("4. Stale loader protection: Out-of-order resolution preserves only the newest loader's items", async () => {
        const loaderA = createDeferredLoader([{label: "Item A", link: "/a"}])
        const loaderB = createDeferredLoader([{label: "Item B", link: "/b"}])

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                load: loaderA.loader,
            },
            global: globalConfig,
        })

        // Switch to loader B before loader A finishes
        await wrapper.setProps({load: loaderB.loader})

        // Resolve B first (newer loader)
        loaderB.resolve()
        await flushPromises()

        const labelsAfterB = wrapper.findAll("[data-testid=\"breadcrumb-entry\"]").map((el) => el.find(".label").text())
        expect(labelsAfterB).toEqual(["Item B"])

        // Resolve A second (older stale loader)
        loaderA.resolve()
        await flushPromises()

        const labelsAfterA = wrapper.findAll("[data-testid=\"breadcrumb-entry\"]").map((el) => el.find(".label").text())
        expect(labelsAfterA).toEqual(["Item B"])
    })

    test("4b. Stale loader protection: A stale loader resolving while the newest is still pending neither shows rows nor the empty message", async () => {
        const loaderA = createDeferredLoader([{label: "Item A", link: "/a"}])
        const loaderB = createDeferredLoader([{label: "Item B", link: "/b"}])

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                load: loaderA.loader,
            },
            global: globalConfig,
        })

        await wrapper.setProps({load: loaderB.loader})

        loaderA.resolve()
        await flushPromises()

        expect(wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")).toHaveLength(0)
        expect(wrapper.find(".empty").exists()).toBe(false)

        loaderB.resolve()
        await flushPromises()

        expect(wrapper.findAll("[data-testid=\"breadcrumb-entry\"]").map((el) => el.find(".label").text())).toEqual(["Item B"])
    })

    test("5. Empty state: Absent while loader pending, appears when resolved with empty array", async () => {
        const deferred = createDeferredLoader<KsBreadcrumbItem[]>([])

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                load: deferred.loader,
            },
            global: globalConfig,
        })

        // Loader is still pending, empty message should not be present
        expect(wrapper.find(".empty").exists()).toBe(false)

        deferred.resolve()
        await flushPromises()

        const emptyEl = wrapper.find(".empty")
        expect(emptyEl.exists()).toBe(true)
        expect(emptyEl.text()).toBe("Nothing here yet")
    })

    test("6. Heading: Displayed when provided, absent when omitted", async () => {
        const wrapperWithoutHeading = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                entries: [{label: "Item 1"}],
            },
            global: globalConfig,
        })
        expect(wrapperWithoutHeading.find(".heading").exists()).toBe(false)

        const wrapperWithHeading = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {
                heading: "In namespaces",
                entries: [{label: "Item 1"}],
            },
            global: globalConfig,
        })
        const headingEl = wrapperWithHeading.find(".heading")
        expect(headingEl.exists()).toBe(true)
        expect(headingEl.text()).toBe("In namespaces")
    })

    test("7. Hover and fly-out: Hovering entry with children opens flyout; hovering entry without closes it", async () => {
        const childrenLoader: KsBreadcrumbLoader = vi.fn().mockResolvedValue([
            {label: "Child 1", link: "/child-1"},
            {label: "Child 2", link: "/child-2"},
        ])

        const entries: KsBreadcrumbItem[] = [
            {label: "Parent", children: childrenLoader},
            {label: "Leaf", link: "/leaf"},
        ]

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {entries},
            global: globalConfig,
            attachTo: document.body,
        })

        const renderedEntries = wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")
        expect(renderedEntries).toHaveLength(2)

        // Initial state: no flyout
        expect(wrapper.find(".flyout").exists()).toBe(false)

        // Hover entry with children
        await hover(renderedEntries[0])

        const flyout = wrapper.find(".flyout")
        expect(flyout.exists()).toBe(true)
        expect(childrenLoader).toHaveBeenCalledTimes(1)
        expect(renderedEntries[0].classes()).toContain("open")

        const childEntries = flyout.findAll("[data-testid=\"breadcrumb-entry\"]")
        const childLabels = childEntries.map((el) => el.find(".label").text())
        expect(childLabels).toEqual(["Child 1", "Child 2"])

        // Hover entry without children -> flyout unmounts
        await hover(renderedEntries[1])

        expect(wrapper.find(".flyout").exists()).toBe(false)
        expect(renderedEntries[0].classes()).not.toContain("open")
        expect(renderedEntries[1].classes()).not.toContain("open")

        wrapper.unmount()
    })

    test("8. Selection propagation: Direct click emits select; nested entry click propagates through parent select", async () => {
        const nestedItem: KsBreadcrumbItem = {label: "Nested Target", link: "/nested-target"}
        const childrenLoader: KsBreadcrumbLoader = vi.fn().mockResolvedValue([nestedItem])

        const directItem: KsBreadcrumbItem = {label: "Direct Target", link: "/direct-target"}
        const parentItem: KsBreadcrumbItem = {label: "Branch", children: childrenLoader}

        const entries: KsBreadcrumbItem[] = [directItem, parentItem]

        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {entries},
            global: globalConfig,
            attachTo: document.body,
        })

        const renderedEntries = wrapper.findAll("[data-testid=\"breadcrumb-entry\"]")

        // 1. Direct entry click
        await renderedEntries[0].trigger("click")
        const selectEvents = wrapper.emitted("select")
        expect(selectEvents).toBeTruthy()
        expect(selectEvents).toHaveLength(1)
        expect(selectEvents![0][0]).toEqual(directItem)

        // 2. Open flyout and click nested entry
        await hover(renderedEntries[1])

        const flyout = wrapper.find(".flyout")
        expect(flyout.exists()).toBe(true)

        const nestedEntry = flyout.find("[data-testid=\"breadcrumb-entry\"]")
        await nestedEntry.trigger("click")

        // Expect parent wrapper to have received second select event with nestedItem
        expect(selectEvents).toHaveLength(2)
        expect(selectEvents![1][0]).toEqual(nestedItem)

        wrapper.unmount()
    })

    test("rules off an entry that starts a group, except when it is the first one", () => {
        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {entries: [{label: "data", divided: true}, {label: "daily", divided: true}, {label: "hourly"}]},
            global: globalConfig,
        })

        const rows = wrapper.findAll("[data-testid=\"breadcrumb-divider\"], [data-testid=\"breadcrumb-entry\"]").map((row) => row.attributes("data-testid"))

        expect(rows).toEqual(["breadcrumb-entry", "breadcrumb-divider", "breadcrumb-entry", "breadcrumb-entry"])
    })

    test("opens nothing and runs no loader for a row the pointer only crosses", async () => {
        const children = vi.fn().mockResolvedValue([{label: "Child"}])
        const wrapper = i18nMount(KsBreadcrumbColumn, {
            locales,
            props: {entries: [{label: "Parent", children}, {label: "Leaf"}]},
            global: globalConfig,
        })
        const row = wrapper.find("[data-testid=\"breadcrumb-entry\"]")

        await row.trigger("mouseenter")
        await row.trigger("mouseleave")
        await new Promise((resolve) => setTimeout(resolve, 170))

        expect(children).not.toHaveBeenCalled()
        expect(wrapper.find(".flyout").exists()).toBe(false)
    })

    test("says it is loading until the loader resolves", async () => {
        const deferred = createDeferredLoader([{label: "daily"}])
        const wrapper = i18nMount(KsBreadcrumbColumn, {locales, props: {load: deferred.loader}, global: globalConfig})

        expect(wrapper.find("[data-testid=\"breadcrumb-loading\"]").text()).toBe("Loading...")

        deferred.resolve()
        await flushPromises()

        expect(wrapper.find("[data-testid=\"breadcrumb-loading\"]").exists()).toBe(false)
    })
})
