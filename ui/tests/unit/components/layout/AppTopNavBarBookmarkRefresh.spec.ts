import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {nextTick, reactive} from "vue"

// Reactive so a test can navigate: the bug under test is a route change the topNav store never
// catches up with, which a route object rebuilt per call cannot express.
const route = reactive({
    fullPath: "/main/flows",
    path: "/main/flows",
    name: "flows/list",
    meta: {},
    params: {},
    query: {},
    hash: "",
})

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({
        resolve: vi.fn((location: string | {path: string; query?: Record<string, unknown>; hash?: string}) => {
            if (typeof location === "string") return {name: "resolved"}
            const query = new URLSearchParams()
            Object.entries(location.query ?? {}).forEach(([key, value]) => query.append(key, String(value)))
            const queryString = query.toString()
            return {
                name: "resolved",
                fullPath: `${location.path}${queryString ? `?${queryString}` : ""}${location.hash ?? ""}`,
            }
        }),
        push: vi.fn(),
    }),
}))

vi.mock("../../../../src/components/layout/GlobalSearch.vue", () => ({
    default: {name: "GlobalSearch", template: "<div />"},
}))

vi.mock("../../../../src/stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({contextInfoBarOpenTab: "", lastContextTab: ""}),
}))

vi.mock("override/components/useLeftMenu", () => ({
    useLeftMenu: () => ({menu: {value: []}}),
}))

import AppTopNavBar from "../../../../src/components/layout/AppTopNavBar.vue"
import {useTopNavStore} from "../../../../src/stores/topNav"
import {useBookmarksStore} from "../../../../src/stores/bookmarks"

const KsTopNavBarStub = {
    name: "KsTopNavBar",
    props: {isBookmarked: {type: Boolean, default: false}},
    template: "<div><slot name=\"search\" /></div>",
}

let wrapper: ReturnType<typeof mount> | undefined

const mountNavBar = () => {
    wrapper = mount(AppTopNavBar, {global: {stubs: {KsTopNavBar: KsTopNavBarStub}}})
    return wrapper
}

describe("AppTopNavBar bookmark label refresh", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        route.fullPath = "/main/flows"
        route.path = "/main/flows"
        route.name = "flows/list"
        route.meta = {}
        route.params = {}
        route.query = {}
        route.hash = ""
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
        localStorage.clear()
    })

    it("should re-derive the label of the page being visited", async () => {
        const bookmarks = useBookmarksStore()
        bookmarks.add({path: "/main/flows", label: "Fluesse"})
        const topNav = useTopNavStore()
        topNav.ownerId = Symbol("owner")
        topNav.title = "Flows"

        mountNavBar()
        await nextTick()

        expect(bookmarks.pages).toEqual([{path: "/main/flows", label: "Flows", custom: false}])
    })

    // The previous bar's ownership is only released a tick after it unmounts, so a route that
    // mounts no TopNavBar of its own leaves a stale owner and a stale title behind — and nothing
    // follows to correct a label written from them.
    it("should leave a bookmark alone on a route that never claims the top nav", async () => {
        const bookmarks = useBookmarksStore()
        bookmarks.add({path: "/main/blueprints/1", label: "Blueprint one"})
        const topNav = useTopNavStore()
        topNav.ownerId = Symbol("owner-of-the-previous-page")
        topNav.title = "Flows"

        mountNavBar()
        await nextTick()

        route.fullPath = "/main/blueprints/1"
        route.path = "/main/blueprints/1"
        await nextTick()

        expect(bookmarks.pages).toEqual([{path: "/main/blueprints/1", label: "Blueprint one", custom: false}])
    })

    it("should re-derive the label once the visited page claims the top nav", async () => {
        const bookmarks = useBookmarksStore()
        bookmarks.add({path: "/main/blueprints/1", label: "Blueprint eins"})
        const topNav = useTopNavStore()
        topNav.ownerId = Symbol("owner-of-the-previous-page")
        topNav.title = "Flows"

        mountNavBar()
        await nextTick()

        route.fullPath = "/main/blueprints/1"
        route.path = "/main/blueprints/1"
        await nextTick()

        // The visited page's own bar mounts and writes its title.
        topNav.ownerId = Symbol("owner-of-the-blueprint")
        topNav.title = "Blueprint one"
        await nextTick()

        expect(bookmarks.pages).toEqual([{path: "/main/blueprints/1", label: "Blueprint one", custom: false}])
    })

    it("should keep a flow bookmark when its default time range is added to the route", async () => {
        route.path = "/main/flows/edit/team/flow/overview"
        route.fullPath = route.path
        route.name = "flows/update/overview"
        route.meta = {tab: "overview"}

        const bookmarks = useBookmarksStore()
        const nav = mountNavBar()
        const topBar = nav.findComponent(KsTopNavBarStub)

        topBar.vm.$emit("star-click")
        expect(bookmarks.pages).toHaveLength(1)

        route.query = {"filters[timeRange][EQUALS]": "PT24H"}
        route.fullPath = `${route.path}?filters%5BtimeRange%5D%5BEQUALS%5D=PT24H`
        await nextTick()

        expect(topBar.props("isBookmarked")).toBe(true)
        topBar.vm.$emit("star-click")
        expect(bookmarks.pages).toHaveLength(0)

        route.query = {"filters[timeRange][EQUALS]": "PT1H"}
        route.fullPath = `${route.path}?filters%5BtimeRange%5D%5BEQUALS%5D=PT1H`
        await nextTick()

        expect(topBar.props("isBookmarked")).toBe(false)
    })
})
