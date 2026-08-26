import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {nextTick, reactive} from "vue"

// Reactive so a test can navigate: the bug under test is a route change the topNav store never
// catches up with, which a route object rebuilt per call cannot express.
const route = reactive({
    fullPath: "/main/flows",
    name: "flows/list",
    meta: {},
    params: {},
    query: {},
})

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({resolve: vi.fn(() => ({name: "resolved"})), push: vi.fn()}),
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

const KsTopNavBarStub = {name: "KsTopNavBar", template: "<div><slot name=\"search\" /></div>"}

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
        await nextTick()

        // The visited page's own bar mounts and writes its title.
        topNav.ownerId = Symbol("owner-of-the-blueprint")
        topNav.title = "Blueprint one"
        await nextTick()

        expect(bookmarks.pages).toEqual([{path: "/main/blueprints/1", label: "Blueprint one", custom: false}])
    })
})
