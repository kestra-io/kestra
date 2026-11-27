import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import BasicSettings from "./BasicSettings.vue"

const success = vi.fn()

vi.mock("../../utils/toast", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../utils/toast")>(),
    useToast: () => ({success}),
}))

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({autocomplete: [], loadAutocomplete: vi.fn()}),
}))

vi.mock("override/components/useLeftMenu", () => ({
    useLeftMenu: () => ({menu: []}),
}))

vi.mock("../../composables/useRouteContext", () => ({default: () => {}}))

describe("BasicSettings", () => {
    afterEach(() => {
        localStorage.clear()
        success.mockReset()
    })

    it("should not save the default namespace when the page opens without one", async () => {
        const pinia = createPinia()
        setActivePinia(pinia)

        i18nMount(BasicSettings, {
            global: {
                plugins: [pinia],
                stubs: {TopNavBar: true, SidebarCustomizeModal: true, KsEditor: {template: "<div />"}},
            },
        })
        await flushPromises()

        expect(success).not.toHaveBeenCalled()
    })
})
