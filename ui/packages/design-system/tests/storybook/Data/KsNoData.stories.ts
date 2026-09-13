import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsNoData from "../../../src/components/Data/KsNoData.vue"

const meta: Meta<typeof KsNoData> = {
    title: "Components/Data/KsNoData",
    component: KsNoData,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "Empty state rendered when there is no data to show — inside KsTable / KsDataTable when there are no rows, or anywhere a centered placeholder is needed. Shows a filter-removed icon, an optional title, and the default 'nothing here / adjust your filters' message. It fills and centers within its container.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsNoData>

export const Default: Story = {
    render: () => ({
        components: {KsNoData},
        template: "<ks-no-data />",
    }),
}

export const WithTitle: Story = {
    render: () => ({
        components: {KsNoData},
        template: "<ks-no-data title=\"No Flows Found\" />",
    }),
}
