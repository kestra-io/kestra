import {afterEach, describe, test, expect} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import {defineComponent, nextTick} from "vue"
import {ElDropdown} from "element-plus"
import KestraDesignSystem from "../../../index"
import KsDropdown from "./KsDropdown.vue"
import KsDropdownItem from "./KsDropdownItem.vue"
import KsDropdownMenu from "./KsDropdownMenu.vue"
import {i18nMount} from "../../../../tests/units/i18nMount"

const globalConfig = {plugins: [KestraDesignSystem]}

const findModifier = (popperOptions: Record<string, unknown> | undefined, name: string) =>
    (popperOptions?.modifiers as Array<Record<string, unknown>>)?.find((m) => m.name === name)

const wrapInDropdown = (slotContent: string) =>
    defineComponent({
        components: {KsDropdown, KsDropdownMenu, KsDropdownItem},
        template: `<ks-dropdown>
            <button>Trigger</button>
            <template #dropdown><ks-dropdown-menu>${slotContent}</ks-dropdown-menu></template>
        </ks-dropdown>`,
    })

describe("KsDropdown", () => {
    test("renders trigger slot", () => {
        const wrapper = mount(KsDropdown, {
            slots: {
                default: "<button>Actions</button>",
                dropdown: defineComponent({
                    components: {KsDropdownMenu, KsDropdownItem},
                    template: "<ks-dropdown-menu><ks-dropdown-item command=\"edit\">Edit</ks-dropdown-item></ks-dropdown-menu>",
                }),
            },
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("keeps the menu away from the viewport edge by default", () => {
        const wrapper = mount(KsDropdown, {
            slots: {default: "<button>Actions</button>"},
            global: globalConfig,
        })

        const popperOptions = wrapper.getComponent(ElDropdown).props("popperOptions") as Record<string, unknown>

        expect(findModifier(popperOptions, "preventOverflow")?.options).toEqual({rootBoundary: "viewport", padding: 8})
        expect(findModifier(popperOptions, "flip")?.options).toEqual({rootBoundary: "viewport", padding: 8})
    })

    test("lets a caller override the default popper-options", () => {
        const custom = {strategy: "fixed", modifiers: [{name: "offset", options: {offset: [0, 12]}}]}
        const wrapper = mount(KsDropdown, {
            slots: {default: "<button>Actions</button>"},
            attrs: {popperOptions: custom},
            global: globalConfig,
        })

        expect(wrapper.getComponent(ElDropdown).props("popperOptions")).toEqual(custom)
    })
})

describe("KsDropdown split button", () => {
    let wrapper: VueWrapper | undefined

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    const mountSplit = (props: Record<string, unknown>) => {
        wrapper = i18nMount(KsDropdown, {
            props: {splitButton: true, ...props},
            slots: {
                default: "Save",
                dropdown: defineComponent({
                    components: {KsDropdownMenu, KsDropdownItem},
                    template: "<ks-dropdown-menu><ks-dropdown-item>Save as draft</ks-dropdown-item></ks-dropdown-menu>",
                }),
            },
            attachTo: document.body,
        })
        const [main, caret] = wrapper.findAll("button")
        return {wrapper, main, caret}
    }

    test("mainButtonDisabled blocks the label button only", async () => {
        const {wrapper, main, caret} = mountSplit({mainButtonDisabled: true})

        expect(main.attributes("disabled")).toBeDefined()
        expect(caret.attributes("disabled")).toBeUndefined()

        await main.trigger("click")
        expect(wrapper.emitted("click")).toBeUndefined()
    })

    test("disabled blocks both halves", () => {
        const {main, caret} = mountSplit({disabled: true})

        expect(main.attributes("disabled")).toBeDefined()
        expect(caret.attributes("disabled")).toBeDefined()
    })
})

describe("KsDropdownItem", () => {
    test("renders with command prop", () => {
        const wrapper = mount(wrapInDropdown("<ks-dropdown-item command=\"edit\">Edit</ks-dropdown-item>"), {
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("disabled prop is accepted", () => {
        const wrapper = mount(wrapInDropdown("<ks-dropdown-item command=\"delete\" :disabled=\"true\">Delete</ks-dropdown-item>"), {
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("danger prop marks the rendered item", async () => {
        mount(wrapInDropdown("<ks-dropdown-item danger>Delete</ks-dropdown-item>"), {
            attrs: {persistent: true},
            global: globalConfig,
        })
        await nextTick()
        expect(document.body.querySelector("li.is-danger")).not.toBeNull()
    })
})

describe("KsDropdownMenu", () => {
    test("renders default slot", () => {
        const wrapper = mount(wrapInDropdown("<li>Item</li>"), {
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })
})
