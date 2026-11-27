import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {shallowMount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {useAuthStore} from "override/stores/auth"
import action from "../../../../src/models/action"

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({name: "flows/list", params: {tenant: "main"}, query: {}, meta: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), resolve: vi.fn(() => ({href: ""}))}),
}))

import Flows from "../../../../src/components/flows/Flows.vue"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

function navBarLabels() {
    const wrapper = shallowMount(Flows, {
        global: {
            plugins: [i18n],
            mocks: {$route: {query: {}}},
            stubs: {
                TopNavBar: {template: "<div><slot name=\"actions\" /></div>"},
                NavBarActions: {template: "<div><slot /><slot name=\"primary\" /></div>"},
                KsDataTable: {template: "<div />"},
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
