import {describe, expect, it, vi} from "vitest"
import {defineComponent, nextTick, reactive} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import type {Flow} from "../../stores/flow"

const route = reactive({params: {namespace: "company.team", id: "first"}, query: {}, meta: {tab: "triggers"}, name: "flows/update/triggers"})

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({replace: vi.fn()}),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

vi.mock("../../stores/flow", async () => {
    const vue = await import("vue")
    const flowStore = vue.reactive({
        flow: undefined as Flow | undefined,
        dependenciesCount: undefined,
        loadDependencies: vi.fn(),
        loadFlow: vi.fn(() => Promise.resolve()),
        loadGraph: vi.fn(),
        loadHasPublishedRevision: vi.fn(() => Promise.resolve()),
        isCreating: false,
    })
    return {useFlowStore: () => flowStore}
})

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {hasAny: () => true, isAllowed: () => true}}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {chartDefaultDuration: "P30D"}}),
}))

vi.mock("../../composables/useRouteContext", () => ({default: vi.fn()}))

import {useFlowStore} from "../../stores/flow"
import FlowRoot from "./FlowRoot.vue"

describe("FlowRoot", () => {
    it("remounts the tab content once the route and the loaded flow both point at another flow", async () => {
        const flowStore = useFlowStore()
        flowStore.flow = {id: "first", namespace: "company.team", source: ""}
        let mounts = 0
        mount(FlowRoot, {
            global: {
                stubs: {
                    FlowRootTopBar: true,
                    Tabs: defineComponent({setup: () => {
                        mounts++
                        return () => null
                    }}),
                },
            },
        })
        await flushPromises()
        expect(mounts).toBe(1)

        flowStore.flow = {id: "first", namespace: "company.team", source: "", revision: 2}
        await nextTick()
        expect(mounts).toBe(1)

        flowStore.flow = {id: "second", namespace: "company.team", source: ""}
        await nextTick()
        expect(mounts).toBe(1)

        route.params = {namespace: "company.team", id: "second"}
        await flushPromises()
        expect(mounts).toBe(2)
    })
})
