import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsLogoBadge from "../../../src/components/Kestra/KsLogoBadge.vue"

const meta: Meta<typeof KsLogoBadge> = {
    title: "Components/Kestra/KsLogoBadge",
    component: KsLogoBadge,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "Kestra monogram framed in a success-bordered box with a check badge. Used on the setup success screens (OSS BasicAuthSetup + EE InstanceSetup). Toggle the Storybook theme to check light / dark."}},
    },
}
export default meta
type Story = StoryObj<typeof KsLogoBadge>

export const Default: Story = {
    render: () => ({
        components: {KsLogoBadge},
        template: "<ks-logo-badge />",
    }),
}
