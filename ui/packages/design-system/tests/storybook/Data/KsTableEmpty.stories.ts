import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsTableEmpty from "../../../src/components/Data/KsTableEmpty.vue"

const meta: Meta<typeof KsTableEmpty> = {
    title: "Components/Data/KsTableEmpty",
    component: KsTableEmpty,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "Empty state rendered inside KsTable / KsDataTable when there are no rows. Shows a filter-removed icon, an optional title, and the default 'nothing here / adjust your filters' message. It fills and centers within KsListingPage / a list container.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsTableEmpty>

export const Default: Story = {
    render: () => ({
        components: {KsTableEmpty},
        template: "<ks-table-empty />",
    }),
}

export const WithTitle: Story = {
    render: () => ({
        components: {KsTableEmpty},
        template: "<ks-table-empty title=\"No Flows Found\" />",
    }),
}
