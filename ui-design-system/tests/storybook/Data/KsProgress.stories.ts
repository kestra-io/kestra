import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsProgress from "../../../src/components/Data/KsProgress.vue"

const meta: Meta<typeof KsProgress> = {
    title: "Components/Data/KsProgress",
    component: KsProgress,
    tags: ["autodocs"],
    argTypes: {
        percentage: {control: {type: "range", min: 0, max: 100, step: 1}},
        type: {control: "select", options: ["line", "circle", "dashboard"]},
        status: {control: "select", options: ["", "success", "exception", "warning"]},
        showText: {control: "boolean"},
        striped: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsProgress is the Kestra design-system abstraction over `ElProgress` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsProgress>

export const Default: Story = {
    render: (args) => ({
        components: {KsProgress},
        setup() { return {args} },
        template: `<div style="padding:24px;width:300px"><ks-progress v-bind="args" /></div>`,
    }),
    args: {percentage: 70},
}

export const Statuses: Story = {
    render: () => ({
        components: {KsProgress},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:300px">
                <ks-progress :percentage="100" status="success" />
                <ks-progress :percentage="70" />
                <ks-progress :percentage="50" status="warning" />
                <ks-progress :percentage="30" status="exception" />
            </div>
        `,
    }),
}

export const Circle: Story = {
    render: () => ({
        components: {KsProgress},
        template: `
            <div style="padding:24px;display:flex;gap:24px;align-items:center">
                <ks-progress :percentage="25" type="circle" />
                <ks-progress :percentage="75" type="circle" status="success" />
                <ks-progress :percentage="50" type="dashboard" />
            </div>
        `,
    }),
}
