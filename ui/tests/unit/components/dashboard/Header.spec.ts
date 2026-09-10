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

import Header from "../../../../src/components/dashboard/components/Header.vue"
import {i18nShallowMount} from "../../i18nMount"

function mountHeader(dashboard: any) {
    return i18nShallowMount(Header, {messages: {overview: "Overview"}, props: {dashboard}})
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

    it("falls back to 'Overview' when the dashboard title is an empty string (not just undefined)", () => {
        const wrapper = mountHeader({id: "default", title: "", deleted: false, charts: []})

        expect(document.title).toBe("Overview | Kestra EE")
        wrapper.unmount()
    })

    it("uses the dashboard's title once it is set", () => {
        const wrapper = mountHeader({id: "default", title: "Default Dashboard", deleted: false, charts: []})

        expect(document.title).toBe("Default Dashboard | Kestra EE")
        wrapper.unmount()
    })
})
