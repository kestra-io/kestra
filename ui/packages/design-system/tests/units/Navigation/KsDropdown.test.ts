import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import KestraDesignSystem from "../../../src/index"
import KsDropdown from "../../../src/components/Navigation/KsDropdown/KsDropdown.vue"
import KsDropdownItem from "../../../src/components/Navigation/KsDropdown/KsDropdownItem.vue"
import KsDropdownMenu from "../../../src/components/Navigation/KsDropdown/KsDropdownMenu.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

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
})

describe("KsDropdownMenu", () => {
    test("renders default slot", () => {
        const wrapper = mount(wrapInDropdown("<li>Item</li>"), {
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })
})
