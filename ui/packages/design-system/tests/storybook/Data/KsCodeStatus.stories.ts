import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsCodeStatus from "../../../src/components/Data/KsCodeStatus.vue"

const meta: Meta<typeof KsCodeStatus> = {
    title: "Components/Data/KsCodeStatus",
    component: KsCodeStatus,
    tags: ["autodocs"],
    argTypes: {
        status: {control: "select", options: ["valid", "error", "warning", "info"]},
        label: {control: "text"},
        iconOnly: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsCodeStatus is a compact badge that surfaces a validity / severity state (typically next to a code block or in the flow editor). It supports `valid`, `error`, `warning` and `info` variants. The label is fully controlled by the caller via the `label` prop or the default slot — wire it to your own i18n. Use `iconOnly` to collapse the badge to its icon for tight responsive layouts.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsCodeStatus>

export const Valid: Story = {
    render: (args) => ({
        components: {KsCodeStatus},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-code-status v-bind=\"args\" /></div>",
    }),
    args: {status: "valid", label: "Valid"},
}

export const Error: Story = {
    render: (args) => ({
        components: {KsCodeStatus},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-code-status v-bind=\"args\" /></div>",
    }),
    args: {status: "error", label: "Error(s)"},
}

export const Warning: Story = {
    render: (args) => ({
        components: {KsCodeStatus},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-code-status v-bind=\"args\" /></div>",
    }),
    args: {status: "warning", label: "Warning detected"},
}

export const Info: Story = {
    render: (args) => ({
        components: {KsCodeStatus},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-code-status v-bind=\"args\" /></div>",
    }),
    args: {status: "info", label: "Informative notice"},
}

export const ErrorWithCount: Story = {
    render: () => ({
        components: {KsCodeStatus},
        template: `
            <div style="padding:24px;display:flex;gap:8px;align-items:center">
                <ks-code-status status="error" label="1 Error" />
                <ks-code-status status="error" label="3 Errors" />
                <ks-code-status status="error" label="42 Errors" />
            </div>
        `,
    }),
}

export const IconOnly: Story = {
    render: () => ({
        components: {KsCodeStatus},
        template: `
            <div style="padding:24px;display:flex;gap:8px;align-items:center;flex-wrap:wrap">
                <ks-code-status status="valid" label="Valid" icon-only />
                <ks-code-status status="error" label="3 Errors" icon-only />
                <ks-code-status status="warning" label="Warning detected" icon-only />
                <ks-code-status status="info" label="Informative notice" icon-only />
            </div>
        `,
    }),
    parameters: {
        docs: {description: {story: "When `iconOnly` is true the textual label is hidden and the badge collapses to a square icon — used for responsive editor toolbars on narrow viewports. The `label` prop should still be set so it remains in the DOM for tooltips / screen readers."}},
    },
}

export const SlotContent: Story = {
    render: () => ({
        components: {KsCodeStatus},
        template: `
            <div style="padding:24px;display:flex;gap:8px;align-items:center">
                <ks-code-status status="valid">All good</ks-code-status>
                <ks-code-status status="error">Needs review</ks-code-status>
            </div>
        `,
    }),
}

export const AllVariants: Story = {
    render: () => ({
        components: {KsCodeStatus},
        template: `
            <div style="padding:24px;display:flex;gap:8px;align-items:center;flex-wrap:wrap">
                <ks-code-status status="valid" label="Valid" />
                <ks-code-status status="error" label="Error(s)" />
                <ks-code-status status="warning" label="Warning detected" />
                <ks-code-status status="info" label="Informative notice" />
            </div>
        `,
    }),
}
