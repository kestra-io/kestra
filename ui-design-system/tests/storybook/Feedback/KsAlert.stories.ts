import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsAlert from "../../../src/components/Feedback/KsAlert.vue"

const meta: Meta<typeof KsAlert> = {
    title: "Components/Feedback/KsAlert",
    component: KsAlert,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["success", "warning", "info", "error"]},
        showIcon: {control: "boolean"},
        closable: {control: "boolean"},
        effect: {control: "select", options: ["light", "dark"]},
        center: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsAlert is the Kestra design-system abstraction over `ElAlert` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsAlert>

export const Default: Story = {
    render: (args) => ({
        components: {KsAlert},
        setup() { return {args} },
        template: `<div style="padding:24px"><ks-alert v-bind="args" /></div>`,
    }),
    args: {type: "info", title: "This is an info alert", showIcon: true},
}

export const Types: Story = {
    render: () => ({
        components: {KsAlert},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-alert type="success" title="Success message" show-icon />
                <ks-alert type="info" title="Info message" show-icon />
                <ks-alert type="warning" title="Warning message" show-icon />
                <ks-alert type="error" title="Error message" show-icon />
            </div>
        `,
    }),
}

export const WithDescription: Story = {
    render: () => ({
        components: {KsAlert},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-alert
                    type="warning"
                    title="Deprecation Warning"
                    description="This feature will be removed in the next major version."
                    show-icon
                    :closable="false"
                />
            </div>
        `,
    }),
}

export const DarkEffect: Story = {
    render: () => ({
        components: {KsAlert},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-alert type="success" title="Success" effect="dark" show-icon />
                <ks-alert type="info" title="Info" effect="dark" show-icon />
                <ks-alert type="warning" title="Warning" effect="dark" show-icon />
                <ks-alert type="error" title="Error" effect="dark" show-icon />
            </div>
        `,
    }),
}
