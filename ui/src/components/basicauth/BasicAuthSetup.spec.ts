import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import BasicAuthSetup from "./BasicAuthSetup.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))

vi.mock("../../stores/api", () => ({
    useApiStore: () => ({posthogEvents: vi.fn()}),
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {}},
})

const slot = {template: "<div><slot /><slot name=\"header\" /></div>"}

describe("BasicAuthSetup.vue", () => {
    it("renders user form and actions without Bootstrap classes", () => {
        setActivePinia(createPinia())
        const wrapper = mount(BasicAuthSetup, {
            global: {
                plugins: [i18n],
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    Logo: true,
                    KsRow: slot,
                    KsCol: slot,
                    KsSteps: slot,
                    KsStep: true,
                    KsCard: slot,
                    KsText: slot,
                    KsButton: defineComponent({
                        name: "KsButton",
                        template: "<button class=\"ks-button\"><slot /></button>",
                    }),
                    KsForm: slot,
                    KsFormItem: slot,
                    KsInput: defineComponent({
                        name: "KsInput",
                        props: ["modelValue"],
                        template: "<input />",
                    }),
                    KsCheckItem: slot,
                    KsCheckbox: slot,
                    KsTooltip: slot,
                    KsIcon: true,
                    KsPasswordRequirements: true,
                    KsOption: true,
                    KsSelect: true,
                    KsCheckboxGroup: slot,
                    KsLogoBadge: true,
                    CheckBold: true,
                    AccountOutline: true,
                    MessageOutline: true,
                    LightningBolt: true,
                },
            },
        })

        const userActions = wrapper.find(".user-actions")
        expect(userActions.exists()).toBe(true)
        expect(userActions.classes()).not.toContain("d-flex")
        expect(userActions.classes()).not.toContain("justify-content-end")
        expect(userActions.classes()).not.toContain("gap-1")
    })
})
