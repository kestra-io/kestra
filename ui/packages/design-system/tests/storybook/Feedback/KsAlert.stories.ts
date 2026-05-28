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
        template: "<div style=\"padding:24px\"><ks-alert v-bind=\"args\" /></div>",
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

/** With icon and description – all types */
export const WithNoIconAndDescription: Story = {
    render: () => ({
        components: {KsAlert},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-alert type="success" title="Execution completed" description="All 12 tasks finished successfully in 1m 23s." :show-icon="false" :closable="false" />
                <ks-alert type="info" title="Scheduled run" description="Next execution is scheduled for today at 03:00 UTC." :show-icon="false" :closable="false" />
                <ks-alert type="warning" title="Resource limit approaching" description="CPU usage is at 85% of the allocated quota." :show-icon="false" :closable="false" />
                <ks-alert type="error" title="Task failed" description="Task 'fetch-data' failed with exit code 1. Check the logs for details." :show-icon="false" :closable="false" />
            </div>
        `,
    }),
}

/** Centered text */
export const Centered: Story = {
    render: () => ({
        components: {KsAlert},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-alert type="info" title="Centered alert" center show-icon />
                <ks-alert type="success" title="Centered with description" description="Text is horizontally centered." center show-icon :closable="false" />
            </div>
        `,
    }),
}


/** Closable */
export const Closable: Story = {
    render: () => ({
        components: {KsAlert},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-alert type="info" title="Dismiss this" :closable="true" />
                <ks-alert type="warning" title="Dismiss this too" :closable="true" />
            </div>
        `,
    }),
}
