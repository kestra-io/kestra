import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsBadge from "../../../src/components/Data/KsBadge.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsBadge> = {
    title: "Components/Data/KsBadge",
    component: KsBadge,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["primary", "success", "warning", "danger", "info"]},
        isDot: {control: "boolean"},
        hidden: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsBadge is the Kestra design-system abstraction over `ElBadge` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsBadge>

export const Default: Story = {
    render: (args) => ({
        components: {KsBadge, KsButton},
        setup() { return {args} },
        template: `<div style="padding:24px"><ks-badge v-bind="args"><ks-button>Messages</ks-button></ks-badge></div>`,
    }),
    args: {value: 5},
}

export const Types: Story = {
    render: () => ({
        components: {KsBadge, KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:24px">
                <ks-badge :value="3" type="primary"><ks-button>Primary</ks-button></ks-badge>
                <ks-badge :value="5" type="success"><ks-button>Success</ks-button></ks-badge>
                <ks-badge :value="2" type="danger"><ks-button>Danger</ks-button></ks-badge>
                <ks-badge is-dot><ks-button>Dot</ks-button></ks-badge>
            </div>
        `,
    }),
}

export const MaxValue: Story = {
    render: () => ({
        components: {KsBadge, KsButton},
        template: `
            <div style="padding:24px;display:flex;gap:24px">
                <ks-badge :value="100" :max="99"><ks-button>Capped at 99</ks-button></ks-badge>
                <ks-badge :value="0" show-zero><ks-button>Show Zero</ks-button></ks-badge>
            </div>
        `,
    }),
}
