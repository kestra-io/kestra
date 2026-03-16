import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsDropdown from "../../../src/components/Navigation/KsDropdown/KsDropdown.vue"
import KsDropdownItem from "../../../src/components/Navigation/KsDropdown/KsDropdownItem.vue"
import KsDropdownMenu from "../../../src/components/Navigation/KsDropdown/KsDropdownMenu.vue"

const meta: Meta<typeof KsDropdown> = {
    title: "Components/Navigation/KsDropdown",
    component: KsDropdown,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "KsDropdown is the Kestra design-system abstraction over `ElDropdown` from Element Plus.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsDropdown>

export const Default: Story = {
    render: () => ({
        components: {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu},
        template: `
            <div style="padding:48px">
                <ks-dropdown>
                    <ks-button type="primary">Actions <span style="font-size:0.7em">▼</span></ks-button>
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item command="edit">Edit</ks-dropdown-item>
                            <ks-dropdown-item command="duplicate">Duplicate</ks-dropdown-item>
                            <ks-dropdown-item divided command="delete" disabled>Delete</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
            </div>
        `,
    }),
}
