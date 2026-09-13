import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsTopologyDetails from "../../../src/components/Data/KsTopologyDetails.vue"

const meta: Meta<typeof KsTopologyDetails> = {
    title: "Components/Data/KsTopologyDetails",
    component: KsTopologyDetails,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "Key/value definition grid used by plugin topology-details slots. Plugins pass `rows`; styling stays in the design system.",
            },
        },
    },
}

export default meta
type Story = StoryObj<typeof KsTopologyDetails>

const render: Story["render"] = (args) => ({
    components: {KsTopologyDetails},
    setup() {
        return {args}
    },
    template: "<div style=\"width: 273px; padding: 24px\"><ks-topology-details v-bind=\"args\" /></div>",
})

export const Default: Story = {
    render,
    args: {
        rows: [
            {label: "Provider", value: "Open AI - gpt-5-nano"},
            {label: "Memory", value: "JOHN"},
            {label: "Tool", value: "DockerMcpClient"},
        ],
    },
}

export const LongValues: Story = {
    render,
    args: {
        rows: [
            {label: "Compute env", value: "arn:aws:batch:us-east-1:123456789012:compute-environment/kestraFargate"},
            {label: "Region", value: "us-east-1"},
        ],
    },
}
