import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {reactive} from "vue"
import {createI18n} from "vue-i18n"

// Reactive so a test can move to the full-page copilot, where the button must disappear.
const route = reactive({
    fullPath: "/main/flows",
    name: "flows/list" as string,
    path: "/main/flows",
    meta: {},
    params: {},
    query: {},
})

// `useLeftMenu` hands out hrefs already resolved to path strings, so the router mock maps those
// paths (and still-raw route objects) back to route names the way the real router would.
const routeNames: Record<string, string> = {
    "/main/dashboards": "home",
    "/main/ai": "ai",
    "/main/flows": "flows/list",
}

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({
        resolve: vi.fn((to: string | {name?: string}) => ({name: typeof to === "string" ? routeNames[to] : to.name})),
        push: vi.fn(),
    }),
}))

vi.mock("../../../../src/components/layout/GlobalSearch.vue", () => ({
    default: {name: "GlobalSearch", template: "<div />"},
}))

vi.mock("../../../../src/stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
}))

// Mirrors the store contract the button relies on: `openCopilot` selects the dock's copilot tab.
const miscStore = reactive({
    contextInfoBarOpenTab: "",
    lastContextTab: "ai",
    openCopilot: vi.fn(() => {
        miscStore.lastContextTab = "ai"
        miscStore.contextInfoBarOpenTab = "ai"
    }),
})

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => miscStore,
}))

// Shape of the left menu as `useLeftMenu` builds it: a Workspace section holding the copilot item,
// every href already turned into a path string.
const menu = reactive<{value: unknown[]}>({value: []})
const workspaceWithCopilot = (copilotHidden = false) => [{
    title: "Workspace",
    child: [
        {title: "Dashboards", href: "/main/dashboards", routes: ["home"]},
        {title: "AI Copilot", href: "/main/ai", routes: ["ai"], hidden: copilotHidden},
        {title: "Flows", href: "/main/flows", routes: ["flows/list"]},
    ],
}]

vi.mock("override/components/useLeftMenu", () => ({
    useLeftMenu: () => ({menu}),
}))

import AppTopNavBar from "../../../../src/components/layout/AppTopNavBar.vue"

const KsTopNavBarStub = {name: "KsTopNavBar", template: "<div><slot name=\"panel-toggle\" /></div>"}

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {ai: {copilot: {title: "AI Copilot"}}}},
})

const mountNavBar = () => mount(AppTopNavBar, {
    global: {plugins: [i18n], stubs: {KsTopNavBar: KsTopNavBarStub}},
})

const copilotButton = (wrapper: ReturnType<typeof mountNavBar>) => wrapper.find("[data-testid=\"topnav-copilot-button\"]")

describe("AppTopNavBar AI Copilot button", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        route.name = "flows/list"
        menu.value = workspaceWithCopilot()
        miscStore.contextInfoBarOpenTab = ""
        miscStore.openCopilot.mockClear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("shows a labelled AI Copilot button when the left menu offers the copilot", () => {
        const button = copilotButton(mountNavBar())

        expect(button.exists()).toBe(true)
        expect(button.text()).toBe("AI Copilot")
        expect(button.attributes("aria-pressed")).toBe("false")
    })

    it("opens the copilot dock tab on click", async () => {
        const wrapper = mountNavBar()

        await copilotButton(wrapper).trigger("click")

        expect(miscStore.openCopilot).toHaveBeenCalledTimes(1)
        expect(miscStore.contextInfoBarOpenTab).toBe("ai")
        expect(copilotButton(wrapper).attributes("aria-pressed")).toBe("true")
        expect(copilotButton(wrapper).classes()).toContain("is-open")
    })

    it("closes the dock when the copilot tab is already the open one", async () => {
        miscStore.contextInfoBarOpenTab = "ai"
        const wrapper = mountNavBar()

        await copilotButton(wrapper).trigger("click")

        expect(miscStore.openCopilot).not.toHaveBeenCalled()
        expect(miscStore.contextInfoBarOpenTab).toBe("")
    })

    it("switches to the copilot tab when another dock tab is open", async () => {
        miscStore.contextInfoBarOpenTab = "docs"
        const wrapper = mountNavBar()

        expect(copilotButton(wrapper).classes()).not.toContain("is-open")

        await copilotButton(wrapper).trigger("click")

        expect(miscStore.openCopilot).toHaveBeenCalledTimes(1)
        expect(miscStore.contextInfoBarOpenTab).toBe("ai")
    })

    it("follows the left menu: hidden copilot item, no button", () => {
        menu.value = workspaceWithCopilot(true)

        expect(copilotButton(mountNavBar()).exists()).toBe(false)
    })

    it("renders nothing without a copilot item in the menu", () => {
        menu.value = [{title: "Workspace", child: [{title: "Flows", href: "/main/flows", routes: ["flows/list"]}]}]

        expect(copilotButton(mountNavBar()).exists()).toBe(false)
    })

    it("also recognises a copilot item whose href is still a route object", () => {
        menu.value = [{title: "Workspace", child: [{title: "AI Copilot", href: {name: "ai"}, routes: ["ai"]}]}]

        expect(copilotButton(mountNavBar()).exists()).toBe(true)
    })

    it("stays out of the way on the full-page copilot", async () => {
        const wrapper = mountNavBar()
        expect(copilotButton(wrapper).exists()).toBe(true)

        route.name = "ai"
        await wrapper.vm.$nextTick()

        expect(copilotButton(wrapper).exists()).toBe(false)
    })
})
