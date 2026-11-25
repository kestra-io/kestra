import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import BasicSettings from "../../../../src/components/settings/BasicSettings.vue"

const success = vi.fn()

vi.mock("../../../../src/utils/toast", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../../../src/utils/toast")>(),
    useToast: () => ({success}),
}))

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({autocomplete: [], loadAutocomplete: vi.fn()}),
}))

vi.mock("override/components/useLeftMenu", () => ({
    useLeftMenu: () => ({menu: []}),
}))

vi.mock("../../../../src/composables/useRouteContext", () => ({default: () => {}}))

describe("BasicSettings", () => {
    afterEach(() => {
        localStorage.clear()
        success.mockReset()
    })

    it("should not save the default namespace when the page opens", async () => {
        localStorage.setItem("defaultNamespace", "company.team")
        const pinia = createPinia()
        setActivePinia(pinia)

        mount(BasicSettings, {
            global: {
                plugins: [createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false}), KestraDesignSystem, pinia],
                stubs: {TopNavBar: true, SidebarCustomizeModal: true, KsEditor: {name: "KsEditor", template: "<div />"}},
            },
        })
        await flushPromises()

        expect(success).not.toHaveBeenCalled()
    })
})
