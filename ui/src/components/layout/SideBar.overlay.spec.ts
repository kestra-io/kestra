import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import SideBar from "./SideBar.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const {route} = await vi.hoisted(async () => {
    const {reactive} = await import("vue")
    return {route: reactive({fullPath: "/flows", path: "/flows", name: "flows/list", params: {}})}
})

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), resolve: vi.fn()}),
    RouterLink: {props: ["to"], template: "<slot :href=\"to\" :navigate=\"() => {}\" />"},
}))

vi.mock("override/components/useLeftMenu", () => ({useLeftMenu: () => ({menu: []})}))

type Listener = (event: {matches: boolean}) => void

let listeners: Listener[] = []
let mobile = false

const setMobile = (value: boolean) => {
    mobile = value
    listeners.forEach((listener) => listener({matches: value}))
}

const harness = defineComponent({
    components: {SideBar},
    setup() {
        const collapsed = ref(mobile)
        const menu = [{id: "flows", title: "Flows", href: "/flows"}]
        return {collapsed, menu}
    },
    template: `
        <div id="app-body">
            <SideBar :menu="menu" :collapsed="collapsed" @menu-collapse="collapsed = $event" />
            <main><input id="behind" /></main>
            <div id="bottom-actions-slot"><button>Restart</button></div>
            <button data-testid="topnav-sidebar-toggle" />
        </div>
    `,
})

const mountHarness = () => i18nMount(harness, {
    attachTo: document.body,
    global: {stubs: {SidebarCustomizeModal: true, BookmarkLinkList: true}},
})

const attr = (selector: string, name: string) => document.querySelector(selector)?.hasAttribute(name)

describe("SideBar overlay", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        listeners = []
        mobile = false
        route.fullPath = "/flows"
        route.path = "/flows"
        vi.stubGlobal("matchMedia", (query: string) => ({
            get matches() { return mobile },
            media: query,
            addEventListener: (_: string, listener: Listener) => listeners.push(listener),
            removeEventListener: vi.fn(),
            addListener: (listener: Listener) => listeners.push(listener),
            removeListener: vi.fn(),
        }))
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        document.body.innerHTML = ""
        localStorage.clear()
    })

    it("makes everything behind the open drawer inert, the bottom bar included", async () => {
        // Given
        mobile = true
        const wrapper = mountHarness()
        await flushPromises()

        // When
        wrapper.vm.collapsed = false
        await flushPromises()

        // Then
        expect(attr("main", "inert")).toBe(true)
        expect(attr("#bottom-actions-slot", "inert")).toBe(true)
        expect(attr("#side-menu", "inert")).toBe(false)
        wrapper.unmount()
    })

    it("makes the collapsed drawer inert and releases the content", async () => {
        // Given
        mobile = true
        const wrapper = mountHarness()
        await flushPromises()
        wrapper.vm.collapsed = false
        await flushPromises()

        // When
        wrapper.vm.collapsed = true
        await flushPromises()

        // Then
        expect(attr("#side-menu", "inert")).toBe(true)
        expect(attr("main", "inert")).toBe(false)
        expect(attr("#bottom-actions-slot", "inert")).toBe(false)
        wrapper.unmount()
    })

    it("closes on Escape", async () => {
        // Given
        mobile = true
        const wrapper = mountHarness()
        wrapper.vm.collapsed = false
        await flushPromises()

        // When
        window.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
        await flushPromises()

        // Then
        expect(wrapper.vm.collapsed).toBe(true)
        wrapper.unmount()
    })

    it("closes when the scrim is tapped", async () => {
        // Given
        mobile = true
        const wrapper = mountHarness()
        wrapper.vm.collapsed = false
        await flushPromises()

        // When
        await wrapper.find("[data-test=sidebar-scrim]").trigger("click")

        // Then
        expect(wrapper.vm.collapsed).toBe(true)
        wrapper.unmount()
    })

    it("closes on navigation", async () => {
        // Given
        mobile = true
        const wrapper = mountHarness()
        wrapper.vm.collapsed = false
        await flushPromises()

        // When
        route.fullPath = "/executions"
        await flushPromises()

        // Then
        expect(wrapper.vm.collapsed).toBe(true)
        wrapper.unmount()
    })

    it("arrives closed and keeps focus where it was when the viewport shrinks to a phone", async () => {
        // Given
        const wrapper = mountHarness()
        await flushPromises()
        const behind = document.querySelector<HTMLInputElement>("#behind")!
        behind.focus()
        expect(wrapper.vm.collapsed).toBe(false)

        // When
        setMobile(true)
        await flushPromises()
        await new Promise((resolve) => setTimeout(resolve))

        // Then
        expect(wrapper.vm.collapsed).toBe(true)
        expect(document.activeElement).toBe(behind)
        wrapper.unmount()
    })

    it("expands again when an expanded desktop menu went to a phone and came back", async () => {
        // Given
        localStorage.setItem("menuCollapsed", "false")
        const wrapper = mountHarness()
        await flushPromises()
        setMobile(true)
        await flushPromises()
        expect(wrapper.vm.collapsed).toBe(true)

        // When
        setMobile(false)
        await flushPromises()

        // Then
        expect(wrapper.vm.collapsed).toBe(false)
        expect(localStorage.getItem("menuCollapsed")).toBe("false")
        wrapper.unmount()
    })

    it("keeps a menu the user collapsed on desktop collapsed after a round trip", async () => {
        // Given
        localStorage.setItem("menuCollapsed", "true")
        const wrapper = mountHarness()
        await flushPromises()
        setMobile(true)
        await flushPromises()

        // When
        setMobile(false)
        await flushPromises()

        // Then
        expect(wrapper.vm.collapsed).toBe(true)
        wrapper.unmount()
    })
})
