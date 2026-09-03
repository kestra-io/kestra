import {describe, it, expect, afterAll, beforeAll, beforeEach, vi} from "vitest"
import {createI18n} from "vue-i18n"
import {shallowMount} from "@vue/test-utils"

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

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {overview: "Overview"}}, missingWarn: false, fallbackWarn: false})

function mountHeader(dashboard: any) {
    return shallowMount(Header, {props: {dashboard}, global: {plugins: [i18n]}})
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
