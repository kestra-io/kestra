import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({
        fullPath: "/main/dashboards/dash1/edit",
        name: "dashboards/update",
        meta: {},
        params: {},
        query: {},
    }),
    useRouter: () => ({
        resolve: vi.fn(() => ({name: "resolved"})),
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

const KsTopNavBarStub = {name: "KsTopNavBar", template: "<div><slot name=\"search\" /></div>"}

const starCurrentPage = () => {
    const wrapper = mount(AppTopNavBar, {
        global: {stubs: {KsTopNavBar: KsTopNavBarStub}},
    })
    wrapper.findComponent(KsTopNavBarStub).vm.$emit("star-click")
    return useBookmarksStore().pages
}

describe("AppTopNavBar favourite label", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("uses the page provided bookmark label when there is one", () => {
        const topNavStore = useTopNavStore()
        topNavStore.title = "dash1"
        topNavStore.breadcrumb = [{label: "Edit Dashboard"}]
        topNavStore.bookmarkLabel = "dash1"

        expect(starCurrentPage()).toEqual([{path: "/main/dashboards/dash1/edit", label: "dash1"}])
    })

    it("falls back to the breadcrumb and title pair without a bookmark label", () => {
        const topNavStore = useTopNavStore()
        topNavStore.title = "dash1"
        topNavStore.breadcrumb = [{label: "Edit Dashboard"}]

        expect(starCurrentPage()).toEqual([{path: "/main/dashboards/dash1/edit", label: "Edit Dashboard: dash1"}])
    })

    it("falls back to the title alone without a breadcrumb", () => {
        useTopNavStore().title = "Executions"

        expect(starCurrentPage()).toEqual([{path: "/main/dashboards/dash1/edit", label: "Executions"}])
    })
})
