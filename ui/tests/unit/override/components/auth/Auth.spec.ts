import {describe, it, expect, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import Auth from "../../../../../src/override/components/auth/Auth.vue"
import {useMiscStore} from "../../../../../src/override/stores/misc"
import {i18nMount} from "../../../i18nMount"

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

function mountAuth(isBasicAuthManagedByConfig: boolean) {
    setActivePinia(createPinia())
    useMiscStore().configs = {isBasicAuthManagedByConfig}
    return i18nMount(Auth, {
        global: {
            stubs: {
                KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
                KsDropdownMenu: {template: "<div><slot /></div>"},
                KsDropdownItem: {template: "<div><slot /></div>"},
                VersionMenuItem: true,
                ChangePasswordDialog: true,
            },
        },
    })
}

describe("Auth", () => {
    it("hides the change password entry when the credentials are set in the configuration file", () => {
        const wrapper = mountAuth(true)

        expect(wrapper.find("[data-test=change-password-menu-item]").exists()).toBe(false)
    })

    it("offers the change password entry when the credentials were set up from the UI", () => {
        const wrapper = mountAuth(false)

        expect(wrapper.find("[data-test=change-password-menu-item]").exists()).toBe(true)
    })
})
