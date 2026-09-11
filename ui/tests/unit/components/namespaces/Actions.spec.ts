import {describe, test, expect, vi, beforeEach, afterAll} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"

const route = {
    name: "namespaces/update/flows",
    meta: {tab: "flows"} as Record<string, unknown>,
    params: {tenant: "acme", id: "company.team"} as Record<string, string>,
    query: {} as Record<string, string>,
}

vi.mock("vue-router", () => ({
    useRouter: () => ({replace: vi.fn()}),
    useRoute: () => route,
    RouterLink: RouterLinkStub,
}))

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({inheritedKVModalVisible: false, addKvModalVisible: false}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {systemNamespace: "kestra.system"}}),
}))

vi.mock("override/components/dashboard/Selector.vue", () => ({
    default: {name: "Dashboards", template: "<div />"},
}))

import Actions from "../../../../src/override/components/namespaces/Actions.vue"

const messages = {en: {create_flow: "Create Flow", "kv.inherited": "Inherited", "kv.add": "Add"}}

const mountActions = () => mount(Actions, {
    global: {
        plugins: [createI18n({legacy: false, locale: "en", messages}), createPinia()],
        stubs: {
            RouterLink: RouterLinkStub,
            KsButton: {props: ["to"], template: "<a><slot /></a>"},
        },
    },
})

const createFlowTarget = (wrapper: ReturnType<typeof mountActions>) =>
    wrapper.findComponent({name: "Action"}).props("to") as {name: string; params?: Record<string, string>; query?: Record<string, string>}

beforeEach(() => {
    route.params = {tenant: "acme", id: "company.team"}
})

afterAll(() => {
    localStorage.clear()
    sessionStorage.clear()
})

describe("namespace Actions", () => {
    test("opens the flow editor for a regular namespace", () => {
        // Given / When
        const target = createFlowTarget(mountActions())

        // Then
        expect(target.name).toBe("flows/create")
        expect(target.query?.namespace).toBe("company.team")
    })

    test("opens the guided recipe builder for the system namespace", () => {
        // Given — the system namespace has its own guided builder, on a tab child route
        route.params = {tenant: "acme", id: "kestra.system"}

        // When
        const target = createFlowTarget(mountActions())

        // Then
        expect(target.name).toBe("namespaces/update/blueprints")
        expect(target.params).toEqual({tenant: "acme", id: "kestra.system"})
    })
})
