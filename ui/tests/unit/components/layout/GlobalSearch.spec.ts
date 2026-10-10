import {nextTick} from "vue"
import {describe, expect, test, beforeEach, afterEach, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createRouter, createWebHistory} from "vue-router"
import {i18nMount} from "../../i18nMount"
import {useAuthStore} from "override/stores/auth"
import {searchFlows} from "@kestra-io/kestra-sdk/flows"
import {searchNamespaces} from "@kestra-io/kestra-sdk/namespaces"

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    searchFlows: vi.fn(async () => ({results: [], total: 0})),
}))
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({
    searchNamespaces: vi.fn(async () => ({results: [], total: 0})),
}))
vi.mock("override/components/useLeftMenu", () => ({
    useLeftMenu: () => ({menu: {value: []}}),
}))

import GlobalSearch from "../../../../src/components/layout/GlobalSearch.vue"

const searchFlowsMock = vi.mocked(searchFlows)
const searchNamespacesMock = vi.mocked(searchNamespaces)
type FlowPage = Awaited<ReturnType<typeof searchFlows>>
const flowHit = (id: string, namespace: string) =>
    ({id, namespace, disabled: false, draft: false, deleted: false}) as FlowPage["results"][number]

const press = (key: string, extras: KeyboardEventInit = {}, target: EventTarget = window) => {
    target.dispatchEvent(new KeyboardEvent("keydown", {key, bubbles: true, cancelable: true, ...extras}))
}

const mountSearch = () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createRouter({
        history: createWebHistory(),
        routes: [
            {path: "/", name: "home", component: {template: "<div />"}},
            {path: "/flows/:namespace/:id", name: "flows/update", component: {template: "<div />"}},
            {path: "/flows/:namespace/:id/executions", name: "flows/update/executions", component: {template: "<div />"}},
            {path: "/namespaces/:id", name: "namespaces/update", component: {template: "<div />"}},
        ],
    })
    const wrapper = i18nMount(GlobalSearch, {
        attachTo: document.body,
        global: {plugins: [pinia, router]},
        messages: {
            loading: "Loading...",
            no_results_found: "No results found",
            executions: "Executions",
            flows: "Flows",
            namespaces: "Namespaces",
            global_search: {
                search_failed: "Couldn't load flows or namespaces",
                open_flow: "Open flow",
            },
        },
    })
    return {wrapper, router}
}

const openAndQuery = async (value: string) => {
    press("k", {ctrlKey: true})
    await nextTick()
    const input = document.querySelector<HTMLInputElement>(".search-modal input")
    expect(input).not.toBeNull()
    input!.value = value
    input!.dispatchEvent(new Event("input", {bubbles: true}))
    await nextTick()
    return input!
}

describe("GlobalSearch", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.useFakeTimers()
        searchFlowsMock.mockReset()
        searchNamespacesMock.mockReset()
        searchFlowsMock.mockResolvedValue({results: [], total: 0})
        searchNamespacesMock.mockResolvedValue({results: [], total: 0})
    })

    afterEach(() => {
        vi.useRealTimers()
        document.body.innerHTML = ""
    })

    test("Ctrl+K focuses the search box", async () => {
        const {wrapper} = mountSearch()
        try {
            press("k", {ctrlKey: true})
            await nextTick()

            const input = document.querySelector<HTMLInputElement>(".search-modal input")
            expect(input).not.toBeNull()
            expect(document.activeElement).toBe(input)
        } finally {
            wrapper.unmount()
        }
    })

    test("lets Monaco keep Ctrl+K chords", async () => {
        const {wrapper} = mountSearch()
        const monaco = document.createElement("div")
        monaco.className = "monaco-editor"
        const textarea = document.createElement("textarea")
        monaco.appendChild(textarea)
        document.body.appendChild(monaco)
        try {
            press("k", {ctrlKey: true}, textarea)
            await nextTick()

            expect(document.querySelector(".search-modal")).toBeNull()
        } finally {
            monaco.remove()
            wrapper.unmount()
        }
    })

    test("keys each flow and namespace row by its identity", async () => {
        searchFlowsMock.mockResolvedValue({
            results: [
                flowHit("daily", "analytics"),
                flowHit("daily", "billing"),
            ],
            total: 2,
        })
        searchNamespacesMock.mockResolvedValue({
            results: [{id: "analytics"}, {id: "billing"}],
            total: 2,
        })
        const {wrapper} = mountSearch()
        try {
            await openAndQuery("daily")
            await vi.advanceTimersByTimeAsync(200)
            await flushPromises()

            const leaves = [...document.querySelectorAll(".result-leaf")].map(node => node.textContent)
            expect(leaves).toEqual(["daily", "daily", "analytics", "billing"])
            const namespaces = [...document.querySelectorAll(".result-namespace")].map(node => node.textContent)
            expect(namespaces).toEqual(["analytics", "billing"])
        } finally {
            wrapper.unmount()
        }
    })

    test("keeps flow hits when namespace search is forbidden or fails", async () => {
        searchFlowsMock.mockResolvedValue({
            results: [flowHit("daily", "analytics")],
            total: 1,
        })
        const {wrapper} = mountSearch()
        const auth = useAuthStore()
        auth.user!.hasAnyActionOnAnyNamespace = (permission: string) => permission === "FLOW"
        try {
            await openAndQuery("daily")
            await vi.advanceTimersByTimeAsync(200)
            await flushPromises()

            expect(searchNamespacesMock).not.toHaveBeenCalled()
            expect(document.body.textContent).toContain("daily")
            expect(document.body.textContent).not.toContain("Couldn't load flows or namespaces")
        } finally {
            wrapper.unmount()
        }
    })

    test("shows loading instead of an empty miss while the request is in flight", async () => {
        let resolveFlows: (value: FlowPage) => void = () => {}
        searchFlowsMock.mockImplementation(() => new Promise(resolve => {
            resolveFlows = resolve
        }))
        const {wrapper} = mountSearch()
        try {
            await openAndQuery("daily")
            await vi.advanceTimersByTimeAsync(200)
            await nextTick()

            expect(document.body.textContent).toContain("Loading...")
            expect(document.body.textContent).not.toContain("No results found")

            resolveFlows({results: [flowHit("daily", "analytics")], total: 12})
            await flushPromises()

            expect(document.body.textContent).toContain("daily")
            expect(document.querySelector(".result-count")?.textContent).toBe("12")
        } finally {
            wrapper.unmount()
        }
    })

    test("shows a distinct error when one source fails and still renders the other", async () => {
        searchFlowsMock.mockRejectedValue(new Error("boom"))
        searchNamespacesMock.mockResolvedValue({
            results: [{id: "analytics"}],
            total: 1,
        })
        const {wrapper} = mountSearch()
        try {
            await openAndQuery("ana")
            await vi.advanceTimersByTimeAsync(200)
            await flushPromises()

            expect(document.body.textContent).toContain("analytics")
            expect(document.body.textContent).toContain("Couldn't load flows or namespaces")
        } finally {
            wrapper.unmount()
        }
    })

    test("clears stale entity rows as soon as the query changes", async () => {
        searchFlowsMock.mockResolvedValueOnce({
            results: [flowHit("old-flow", "analytics")],
            total: 1,
        })
        const {wrapper} = mountSearch()
        try {
            const input = await openAndQuery("old")
            await vi.advanceTimersByTimeAsync(200)
            await flushPromises()
            expect(document.body.textContent).toContain("old-flow")

            searchFlowsMock.mockImplementation(() => new Promise(() => {}))
            input.value = "new"
            input.dispatchEvent(new Event("input", {bubbles: true}))
            await nextTick()

            expect(document.body.textContent).not.toContain("old-flow")
            expect(document.body.textContent).toContain("Loading...")
        } finally {
            wrapper.unmount()
        }
    })

    test("only opens executions with ArrowRight when the caret is at the end", async () => {
        searchFlowsMock.mockResolvedValue({
            results: [flowHit("daily", "analytics")],
            total: 1,
        })
        const {wrapper, router} = mountSearch()
        const push = vi.spyOn(router, "push").mockResolvedValue()
        try {
            const input = await openAndQuery("daily")
            await vi.advanceTimersByTimeAsync(200)
            await flushPromises()

            input.setSelectionRange(0, 0)
            input.dispatchEvent(new KeyboardEvent("keydown", {key: "ArrowRight", bubbles: true, cancelable: true}))
            await nextTick()
            expect(push).not.toHaveBeenCalled()

            input.setSelectionRange(input.value.length, input.value.length)
            input.dispatchEvent(new KeyboardEvent("keydown", {key: "ArrowRight", bubbles: true, cancelable: true}))
            await nextTick()
            expect(push).toHaveBeenCalledWith(expect.objectContaining({name: "flows/update/executions"}))
        } finally {
            wrapper.unmount()
        }
    })

    test("keeps the executions action outside the result link", async () => {
        searchFlowsMock.mockResolvedValue({
            results: [flowHit("daily", "analytics")],
            total: 1,
        })
        const {wrapper} = mountSearch()
        try {
            await openAndQuery("daily")
            await vi.advanceTimersByTimeAsync(200)
            await flushPromises()

            expect(document.querySelector("a.result-link .result-action")).toBeNull()
            expect(document.querySelector(".result-row > .result-action, .result-row .result-action")).not.toBeNull()
        } finally {
            wrapper.unmount()
        }
    })
})
