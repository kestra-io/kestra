import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsEmptyState from "../../../src/components/Data/KsEmptyState.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            ks_empty_state: {
                learn_more: "Learn more",
            },
        },
    },
})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

describe("KsEmptyState", () => {
    test("renders title and description", () => {
        const wrapper = mount(KsEmptyState, {
            props: {title: "No items", description: "Add one to get started."},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("No items")
        expect(wrapper.text()).toContain("Add one to get started.")
    })

    test("renders artwork only when image is provided", () => {
        const without = mount(KsEmptyState, {
            props: {title: "Nothing"},
            global: globalConfig,
        })
        expect(without.find(".ks-empty-state__artwork").exists()).toBe(false)

        const withImage = mount(KsEmptyState, {
            props: {title: "Nothing", image: "/test.svg"},
            global: globalConfig,
        })
        expect(withImage.find(".ks-empty-state__artwork").exists()).toBe(true)
    })

    test("action slot renders inside the actions row", () => {
        const wrapper = mount(KsEmptyState, {
            props: {title: "Empty"},
            slots: {action: "<button data-test=\"create\">Create</button>"},
            global: globalConfig,
        })
        expect(wrapper.find("[data-test=\"create\"]").exists()).toBe(true)
    })

    test("renders Learn more as a button when learnMore is set", () => {
        const wrapper = mount(KsEmptyState, {
            props: {title: "Empty", learnMore: "https://kestra.io/docs"},
            global: globalConfig,
        })

        const button = wrapper.find(".ks-empty-state__actions a")
        expect(button.exists()).toBe(true)
        expect(button.attributes("href")).toBe("https://kestra.io/docs")
        expect(button.attributes("target")).toBe("_blank")
        // setup.ts mocks vue-i18n, so labels render as their translation key.
        expect(button.text()).toBe("ks_empty_state.learn_more")
    })

    test("omits the actions row when there is no action slot and no learnMore", () => {
        const wrapper = mount(KsEmptyState, {
            props: {title: "Empty"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-empty-state__actions").exists()).toBe(false)
    })

    test("keeps the action slot alongside the Learn more button", () => {
        const wrapper = mount(KsEmptyState, {
            props: {title: "Empty", learnMore: "https://kestra.io/docs"},
            slots: {action: "<button data-test=\"create\">Create</button>"},
            global: globalConfig,
        })

        const actions = wrapper.find(".ks-empty-state__actions")
        expect(actions.find("[data-test=\"create\"]").exists()).toBe(true)
        expect(actions.findAll("a")).toHaveLength(1)
    })

    test("description slot overrides description prop", () => {
        const wrapper = mount(KsEmptyState, {
            props: {title: "Empty", description: "from prop"},
            slots: {description: "<span data-test=\"slot-desc\">from slot</span>"},
            global: globalConfig,
        })
        expect(wrapper.find("[data-test=\"slot-desc\"]").exists()).toBe(true)
        expect(wrapper.text()).not.toContain("from prop")
    })
})
