import {describe, it, expect, beforeEach, afterEach, vi} from "vitest"
import {mount} from "@vue/test-utils"

let edition = "EE"
let allowed = true

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => allowed}}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition, isCustomDashboardsEnabled: true}}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "home"}),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

import Header from "./Header.vue"

const stubs = {
    TopNavBar: {template: "<div><slot name=\"actions\" /></div>"},
    Dashboards: {template: "<div class=\"selector\" />"},
    NavBarActionsDropdown: {template: "<div class=\"kebab\"><slot /></div>"},
    NavBarAction: {props: ["label", "type"], template: "<button :data-type=\"type\">{{ label }}</button>"},
    KsIcon: {template: "<span><slot /></span>"},
}

function mountHeader(dashboard?: Record<string, unknown>) {
    return mount(Header, {
        props: {dashboard},
        global: {stubs, mocks: {$t: (key: string) => key}},
    })
}

describe("dashboard Header actions", () => {
    beforeEach(() => {
        edition = "EE"
        allowed = true
    })

    afterEach(() => {
        document.title = ""
    })

    it("shows only Create Dashboard in the kebab on the default dashboard", () => {
        const wrapper = mountHeader({id: "default"})
        const items = wrapper.find(".kebab").findAll("button").map((b) => b.text())
        expect(items).toEqual(["dashboards.creation.label"])
    })

    it("shows Edit and Create Dashboard in the kebab on a non-default dashboard", () => {
        const wrapper = mountHeader({id: "custom"})
        const items = wrapper.find(".kebab").findAll("button").map((b) => b.text())
        expect(items).toEqual(["dashboards.edition.label", "dashboards.creation.label"])
    })

    it("hides the dashboard actions kebab in OSS", () => {
        edition = "OSS"
        const wrapper = mountHeader({id: "custom"})
        expect(wrapper.find(".kebab").exists()).toBe(false)
    })
})
