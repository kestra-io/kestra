import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h} from "vue"

import OnboardingResourceList, {type OnboardingResourceItem} from "../../../../src/components/onboarding/OnboardingResourceList.vue"

vi.mock("vue-material-design-icons/ArrowRight.vue", () => ({
    default: defineComponent({name: "ArrowRight", setup: () => () => h("span")}),
}))

const RouterLinkStub = defineComponent({
    name: "RouterLink",
    props: {to: {type: [String, Object], default: undefined}},
    setup: (_props, {slots, attrs}) => () => h("a", attrs, slots.default?.()),
})

const Icon = defineComponent({name: "Icon", setup: () => () => h("span")})

const internal: OnboardingResourceItem = {
    titleKey: "t.internal",
    descriptionKey: "d.internal",
    icon: Icon,
    iconClass: "is-blueprints",
    to: {name: "blueprints"},
}

const external: OnboardingResourceItem = {
    titleKey: "t.external",
    descriptionKey: "d.external",
    icon: Icon,
    iconClass: "is-slack",
    href: "https://kestra.io/slack",
}

const mountList = (items: OnboardingResourceItem[]) =>
    mount(OnboardingResourceList, {
        props: {items},
        global: {
            stubs: {RouterLink: RouterLinkStub},
            mocks: {$t: (key: string) => key},
        },
    })

describe("OnboardingResourceList", () => {
    // The Blueprints item is a router-link, so the page navigated underneath while the host
    // dialog stayed on top of it with nothing to close it.
    it("should report a navigation when an in-app item is clicked", async () => {
        const wrapper = mountList([internal])

        await wrapper.find(".onboarding-resource-item").trigger("click", {button: 0})

        expect(wrapper.emitted("navigate")).toHaveLength(1)
    })

    it("should not report a navigation for an external item", async () => {
        const wrapper = mountList([external])

        await wrapper.find(".onboarding-resource-item").trigger("click", {button: 0})

        expect(wrapper.emitted("navigate")).toBeUndefined()
    })

    // A modified click opens a new tab, so the user is still on the page hosting the list.
    it.each([
        ["ctrl", {ctrlKey: true}],
        ["meta", {metaKey: true}],
        ["shift", {shiftKey: true}],
        ["middle button", {button: 1}],
    ])("should not report a navigation for a %s click", async (_label, modifiers) => {
        const wrapper = mountList([internal])

        await wrapper.find(".onboarding-resource-item").trigger("click", {button: 0, ...modifiers})

        expect(wrapper.emitted("navigate")).toBeUndefined()
    })
})
