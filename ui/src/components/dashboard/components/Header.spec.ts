import {describe, it, expect, beforeEach, afterEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
import type {KsBreadcrumbLoader} from "@kestra-io/design-system"

let edition = "EE"
let allowed = true
const list = vi.fn()
const saveDefaults = vi.fn()
const deleteDashboard = vi.fn()
const load = vi.fn()

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => allowed}}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition, isCustomDashboardsEnabled: true}}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "home", params: {tenant: "main"}}),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({list, saveDefaults, delete: deleteDashboard}),
}))

vi.mock("../../../utils/toast", () => ({
    useToast: () => ({confirm: (_message: string, callback: () => Promise<void>) => callback(), deleted: vi.fn()}),
}))

import Header from "./Header.vue"

const TopNavBar = {props: ["titleSiblings", "breadcrumb"], template: "<div><slot name=\"actions\" /></div>"}

const stubs = {
    TopNavBar,
    Dashboards: {template: "<div class=\"selector\" />"},
    NavBarActionsDropdown: {template: "<div class=\"kebab\"><slot /></div>"},
    NavBarAction: {props: ["label", "type"], template: "<button :data-type=\"type\" @click=\"$emit('click')\">{{ label }}</button>"},
}

const DASHBOARDS = [
    {id: "default", title: "default", isDefault: false},
    {id: "custom", title: "Custom", isDefault: true},
]

function mountHeader(dashboard?: Record<string, unknown>) {
    return mount(Header, {
        props: {dashboard, load},
        global: {stubs, mocks: {$t: (key: string) => key}},
    })
}

const kebabItems = (wrapper: ReturnType<typeof mountHeader>) => wrapper.find(".kebab").findAll("button").map((b) => b.text())

const siblingsOf = (wrapper: ReturnType<typeof mountHeader>) => wrapper.findComponent(TopNavBar).props("titleSiblings") as KsBreadcrumbLoader | undefined

describe("dashboard Header actions", () => {
    beforeEach(() => {
        edition = "EE"
        allowed = true
        vi.clearAllMocks()
        list.mockResolvedValue(DASHBOARDS)
    })

    afterEach(() => {
        document.title = ""
    })

    it("shows only Create Dashboard in the kebab on the default dashboard", () => {
        const wrapper = mountHeader({id: "default"})

        expect(kebabItems(wrapper)).toEqual(["dashboards.creation.label"])
    })

    it("scopes the kebab to the open custom dashboard", () => {
        const wrapper = mountHeader({id: "custom"})

        expect(kebabItems(wrapper)).toEqual(["dashboards.edition.label", "dashboards.set_default", "delete", "dashboards.creation.label"])
    })

    it("keeps the locked selector and offers no kebab or breadcrumb switch in OSS", () => {
        edition = "OSS"

        const wrapper = mountHeader({id: "custom"})

        expect(wrapper.find(".kebab").exists()).toBe(false)
        expect(wrapper.find(".selector").exists()).toBe(true)
        expect(siblingsOf(wrapper)).toBeUndefined()
    })

    it("lists the dashboards in the breadcrumb with the open one current and switches through load", async () => {
        const wrapper = mountHeader({id: "custom"})

        const entries = await siblingsOf(wrapper)!()

        expect(entries.map((e) => [e.label, e.current])).toEqual([["dashboards.default", false], ["Custom", true]])
        entries[0].onClick!()
        expect(load).toHaveBeenCalledWith("default")
    })

    it("offers no breadcrumb switch when only the default dashboard exists", async () => {
        list.mockResolvedValue([DASHBOARDS[0]])

        const wrapper = mountHeader({id: "default"})

        expect(await siblingsOf(wrapper)!()).toEqual([])
    })

    it("deletes the open dashboard after confirmation and goes back to the default one", async () => {
        const wrapper = mountHeader({id: "custom", title: "Custom"})

        await wrapper.find(".kebab").findAll("button")[2].trigger("click")
        await Promise.resolve()

        expect(deleteDashboard).toHaveBeenCalledWith("custom")
        expect(load).toHaveBeenCalledWith("default")
    })
})
