import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsCodeStatus from "../../../src/components/Data/KsCodeStatus.vue"

const meta: Meta<typeof KsCodeStatus> = {
    title: "Components/Data/KsCodeStatus",
    component: KsCodeStatus,
    tags: ["autodocs"],
    argTypes: {
        status: {control: "select", options: ["valid", "error"]},
        label: {control: "text"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsCodeStatus is a compact badge that surfaces the validity state of a code block (typically in the flow editor). It supports a `valid` and an `error` variant. The label is fully controlled by the caller via the `label` prop or the default slot — wire it to your own i18n.",
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
            </div>
        `,
    }),
}
