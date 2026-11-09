import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import Namespaces from "./Namespaces.vue"

const {route} = vi.hoisted(() => ({
    route: {query: {} as Record<string, string>, params: {}, name: "namespaces/list"},
}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("../../../composables/useNamespaces", () => ({
    default: () => ({all: vi.fn().mockResolvedValue([])}),
}))

const messages = {
    no_namespaces: "No namespace matches",
    no_namespaces_yet: "No namespaces yet",
}

const mountNamespaces = () => i18nMount(Namespaces, {
    messages,
    global: {
        plugins: [KestraDesignSystem],
        stubs: {Navbar: true, KSFilter: true, Action: true},
    },
})

describe("Namespaces empty state", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        route.query = {}
        document.title = ""
        localStorage.clear()
    })

    test("shouldShowTheNothingYetMessageWhenNoFilterIsApplied", async () => {
        const wrapper = mountNamespaces()
        await flushPromises()

        expect(wrapper.text()).toContain(messages.no_namespaces_yet)
    })

    test("shouldShowTheNoMatchMessageWhenAFilterIsApplied", async () => {
        route.query = {"filters[q][EQUALS]": "missing"}

        const wrapper = mountNamespaces()
        await flushPromises()

        expect(wrapper.text()).toContain(messages.no_namespaces)
        expect(wrapper.text()).not.toContain(messages.no_namespaces_yet)
    })
})
