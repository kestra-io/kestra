import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsLink from "../../../src/components/Basic/KsLink.vue"

const meta: Meta<typeof KsLink> = {
    title: "Components/Basic/KsLink",
    component: KsLink,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["", "default", "primary", "success", "warning", "danger", "info"]},
        underline: {control: "boolean"},
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsLink is the Kestra design-system abstraction over `ElLink` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsLink>

export const Default: Story = {
    render: (args) => ({
        components: {KsLink},
        setup() { return {args} },
        template: `<div style="padding:24px"><ks-link v-bind="args">Click me</ks-link></div>`,
    }),
    args: {type: "primary", href: "#"},
}

export const Types: Story = {
    render: () => ({
        components: {KsLink},
        template: `
            <div style="padding:24px;display:flex;gap:16px;flex-wrap:wrap">
                <ks-link>Default</ks-link>
                <ks-link type="primary">Primary</ks-link>
                <ks-link type="success">Success</ks-link>
                <ks-link type="info">Info</ks-link>
                <ks-link type="warning">Warning</ks-link>
                <ks-link type="danger">Danger</ks-link>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsLink},
        template: `
            <div style="padding:24px;display:flex;gap:16px">
                <ks-link type="primary" disabled>Disabled Link</ks-link>
            </div>
        `,
    }),
}
