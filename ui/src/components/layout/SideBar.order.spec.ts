import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, ref} from "vue"
import SideBar from "./SideBar.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useBookmarksStore} from "../../stores/bookmarks"

const {route} = await vi.hoisted(async () => {
    const {reactive} = await import("vue")
    return {route: reactive({fullPath: "/flows", path: "/flows", name: "flows/list", params: {}})}
})

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), resolve: vi.fn()}),
    RouterLink: {props: ["to", "custom"], template: '<slot :href="to" :navigate="() => {}" />'},
}))

vi.mock("override/components/useLeftMenu", () => ({useLeftMenu: () => ({menu: []})}))

const harness = defineComponent({
    components: {SideBar},
    props: {
        menu: {type: Array, default: () => [{id: "workspace", title: "Workspace", child: [{id: "flows", title: "Flows", href: "/flows"}]}]},
    },
    setup() {
        const collapsed = ref(false)
        return {collapsed}
    },
    template: `
        <div id="app-body">
            <SideBar :menu="menu" :collapsed="collapsed" />
        </div>
    `,
})

let wrapper: ReturnType<typeof i18nMount> | undefined

describe("SideBar order", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
        localStorage.clear()
    })

    it("renders the favourites section above the main menu sections when bookmarks exist", () => {
        const bookmarksStore = useBookmarksStore()
        bookmarksStore.pages = [{label: "My Flow", path: "/flows/my-flow"}]

        wrapper = i18nMount(harness, {
            global: {stubs: {SidebarCustomizeModal: true, BookmarkLinkList: true}},
        })

        const sections = wrapper.findAll(".ks-sidebar-section")
        expect(sections.length).toBeGreaterThanOrEqual(2)
        // First section should be favourites / bookmarks
        expect(sections[0].text()).toContain("favourites")
        // Second section should be workspace
        expect(sections[1].text()).toContain("Workspace")
    })
})
