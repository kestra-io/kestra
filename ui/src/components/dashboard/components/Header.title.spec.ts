import {describe, it, expect, afterAll, beforeAll, beforeEach, vi} from "vitest"
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "home"}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => false}}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))

vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({list: async () => ({results: []}), saveDefaults: vi.fn(), delete: vi.fn()}),
}))

vi.mock("../../../utils/toast", () => ({
    useToast: () => ({confirm: vi.fn(), deleted: vi.fn()}),
}))

import Header from "./Header.vue"
import {i18nShallowMount} from "../../../../tests/unit/i18nMount"

function mountHeader(dashboard: Record<string, unknown>) {
    return i18nShallowMount(Header, {messages: {dashboards: {default: "Default Dashboard"}}, props: {dashboard}})
}

describe("dashboard Header.vue — browser tab title", () => {
    let originalTitle: string

    beforeAll(() => {
        originalTitle = document.title
    })

    afterAll(() => {
        document.title = originalTitle
    })

    beforeEach(() => {
        document.title = "Kestra EE"
    })

    it("falls back to the default dashboard title when the dashboard title is an empty string (not just undefined)", () => {
        const wrapper = mountHeader({id: "default", title: "", deleted: false, charts: []})

        expect(document.title).toBe("Default Dashboard | Kestra EE")
        wrapper.unmount()
    })

    it("uses the dashboard's title once it is set", () => {
        const wrapper = mountHeader({id: "sales", title: "Sales KPIs", deleted: false, charts: []})

        expect(document.title).toBe("Sales KPIs | Kestra EE")
        wrapper.unmount()
    })
})