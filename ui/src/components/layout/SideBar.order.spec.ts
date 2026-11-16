import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
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
    RouterLink: {props: ["to", "custom"], template: "<slot :href=\"to\" :navigate=\"() => {}\" />"},
}))

vi.mock("override/components/useLeftMenu", () => ({useLeftMenu: () => ({menu: []})}))

const menu = [
    {
        id: "workspace",
        title: "Workspace",
        child: [
            {id: "flows", title: "Flows", href: "/flows"},
            {id: "executions", title: "Executions", href: "/executions"},
        ],
    },
]

const mountSideBar = () => i18nMount(SideBar, {
    attachTo: document.body,
    props: {menu},
    global: {stubs: {SidebarCustomizeModal: true, BookmarkLinkList: true}},
})

describe("SideBar favourites ordering", () => {
    beforeEach(() => {
        localStorage.setItem("starred.bookmarks", JSON.stringify([{path: "/flows/my/flow", label: "My Flow"}]))
        setActivePinia(createPinia())
        vi.stubGlobal("matchMedia", (query: string) => ({
            matches: false,
            media: query,
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            addListener: vi.fn(),
            removeListener: vi.fn(),
        }))
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        document.body.innerHTML = ""
        localStorage.clear()
    })

    it("renders the favourites section above the dynamic menu sections", async () => {
        const wrapper = mountSideBar()
        await flushPromises()

        const text = document.body.textContent ?? ""
        expect(text).toContain("favourites")
        expect(text).toContain("Workspace")
        expect(text.indexOf("favourites")).toBeLessThan(text.indexOf("Workspace"))

        wrapper.unmount()
    })
})
