import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsExecutionStatus from "../../../src/components/Data/KsExecutionStatus/KsExecutionStatus.vue"

const statuses = [
    "CREATED", "SUBMITTED", "RESTARTED", "FAILED", "KILLED", "SUCCESS", "RETRIED", "RUNNING", "BREAKPOINT",
    "WARNING", "PAUSED", "RETRYING", "KILLING", "CANCELLED", "SKIPPED", "QUEUED",
] as const

const meta: Meta<typeof KsExecutionStatus> = {
    title: "Components/Data/KsExecutionStatus",
    component: KsExecutionStatus,
    tags: ["autodocs"],
    argTypes: {
        status: {control: "select", options: [...statuses]},
        size: {control: "select", options: ["large", "default", "small"]},
        icon: {control: "boolean"},
        glow: {control: "boolean"},
        title: {control: "text"},
        clickable: {control: "boolean"},
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsExecutionStatus displays an execution status badge with optional icon, color-coded by status."}},
    },
}
export default meta
type Story = StoryObj<typeof KsExecutionStatus>

export const Default: Story = {
    render: (args) => ({
        components: {KsExecutionStatus},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-execution-status v-bind=\"args\" /></div>",
    }),
    args: {status: "SUCCESS"},
}

export const AllStatuses: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        setup() { return {statuses} },
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:8px">
                <ks-execution-status v-for="s in statuses" :key="s" :status="s" />
            </div>
        `,
    }),
}

export const WithIcons: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        setup() { return {statuses} },
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:8px">
                <ks-execution-status v-for="s in statuses" :key="s" :status="s" icon />
            </div>
        `,
    }),
}

export const Sizes: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        template: `
            <div style="padding:24px;display:flex;gap:12px;align-items:center">
                <ks-execution-status status="SUCCESS" icon size="small" />
                <ks-execution-status status="SUCCESS" icon size="default" />
                <ks-execution-status status="SUCCESS" icon size="large" />
            </div>
        `,
    }),
}

export const Glow: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        setup() { return {statuses} },
        template: `
            <div style="padding:48px;display:flex;flex-wrap:wrap;gap:32px">
                <ks-execution-status v-for="s in statuses" :key="s" :status="s" glow />
            </div>
        `,
    }),
}

export const CustomTitle: Story = {
    render: (args) => ({
        components: {KsExecutionStatus},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-execution-status v-bind=\"args\" /></div>",
    }),
    args: {status: "RUNNING", title: "In Progress", icon: true},
}

/**
 * Hover both badges: only the opted-in one shows a pointer. Bootstrap's reboot puts
 * `cursor: pointer` on `[type="button"]:not(:disabled)`, which ties with the component's own
 * scoped rule and wins on source order, so the non-clickable badge used to advertise a click it
 * never handled. It now inherits instead, so a badge inside a clickable row still shows the
 * row's pointer rather than a dead patch.
 */
export const CursorAffordance: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        template: `
            <div style="padding:24px;display:flex;align-items:center;gap:24px">
                <span style="display:flex;flex-direction:column;gap:8px;align-items:flex-start">
                    <small>default — cursor: default</small>
                    <ks-execution-status status="SUCCESS" icon />
                </span>
                <span style="display:flex;flex-direction:column;gap:8px;align-items:flex-start">
                    <small>clickable — cursor: pointer</small>
                    <ks-execution-status status="SUCCESS" icon clickable />
                </span>
            </div>
        `,
    }),
}

export const Clickable: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        setup() { return {statuses} },
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:8px">
                <ks-execution-status v-for="s in statuses" :key="s" :status="s" icon clickable />
            </div>
        `,
    }),
}

export const GlowClickable: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        setup() { return {statuses} },
        template: `
            <div style="padding:48px;display:flex;flex-wrap:wrap;gap:32px">
                <ks-execution-status v-for="s in statuses" :key="s" :status="s" icon glow clickable />
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsExecutionStatus},
        setup() { return {statuses} },
        template: `
            <div style="padding:24px;display:flex;flex-wrap:wrap;gap:8px">
                <ks-execution-status v-for="s in statuses" :key="s" :status="s" icon glow clickable disabled />
            </div>
        `,
    }),
}
