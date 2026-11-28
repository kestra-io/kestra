import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"
import {useAuthStore} from "override/stores/auth"
import action from "../../models/action"

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({name: "flows/list", params: {tenant: "main"}, query: {}, meta: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), resolve: vi.fn(() => ({href: ""}))}),
}))

import Flows from "./Flows.vue"

function navBarLabels() {
    const wrapper = i18nShallowMount(Flows, {
        global: {
            stubs: {
                TopNavBar: {template: "<div><slot name=\"actions\" /></div>"},
                NavBarActions: {template: "<div><slot /><slot name=\"primary\" /></div>"},
            },
        },
    })
    return wrapper.findAllComponents({name: "NavBarAction"}).map(navBarAction => navBarAction.props("label"))
}

describe("Flows import", () => {
    let originalTitle: string

    beforeEach(() => {
        originalTitle = document.title
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.title = originalTitle
    })

    it("offers Import only to a user who can import flows into some namespace", () => {
        expect(navBarLabels()).toContain("import")

        vi.spyOn(useAuthStore().user!, "hasAnyActionOnAnyNamespace").mockImplementation((_resource, actionName) => actionName !== action.IMPORT)

        expect(navBarLabels()).not.toContain("import")
    })
})
