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
        color: {control: "text", type: {name: "string"}},
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
        template: "<div style=\"padding:24px;width:300px\"><ks-progress v-bind=\"args\" /></div>",
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

/** Text inside – percentage label inside the bar */
export const TextInside: Story = {
    render: () => ({
        components: {KsProgress},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:400px">
                <ks-progress :percentage="40" :left="20" text-inside :stroke-width="20" />
                <ks-progress :percentage="70" text-inside :stroke-width="20" status="success" />
                <ks-progress :percentage="90" text-inside :stroke-width="20" status="warning" />
            </div>
        `,
    }),
}

/** Custom color – string, function, or color steps */
export const CustomColor: Story = {
    render: () => ({
        components: {KsProgress},
        setup() {
            const colorFn = (pct: number) => {
                if (pct < 30) return "#f56c6c"
                if (pct < 70) return "#e6a23c"
                return "#67c23a"
            }
            const colorSteps = [
                {color: "#f56c6c", percentage: 30},
                {color: "#e6a23c", percentage: 70},
                {color: "#67c23a", percentage: 100},
            ]
            return {colorFn, colorSteps}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:350px">
                <p style="font-size:12px;opacity:0.5;margin:0">Custom string color</p>
                <ks-progress :percentage="60" color="#7c3aed" />
                <p style="font-size:12px;opacity:0.5;margin:0">Color function</p>
                <ks-progress :percentage="25" :color="colorFn" />
                <ks-progress :percentage="55" :color="colorFn" />
                <ks-progress :percentage="80" :color="colorFn" />
                <p style="font-size:12px;opacity:0.5;margin:0">Color steps array</p>
                <ks-progress :percentage="50" :color="colorSteps" />
            </div>
        `,
    }),
}

/** Striped progress */
export const Striped: Story = {
    render: () => ({
        components: {KsProgress},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:350px">
                <ks-progress :percentage="60" striped :stroke-width="16" />
                <ks-progress :percentage="75" striped striped-flow :stroke-width="16" status="success" />
            </div>
        `,
    }),
}

/** Dashboard progress bar */
export const Dashboard: Story = {
    render: () => ({
        components: {KsProgress},
        template: `
            <div style="padding:24px;display:flex;gap:24px;align-items:center">
                <ks-progress :percentage="33" type="dashboard" />
                <ks-progress :percentage="66" type="dashboard" status="warning" />
                <ks-progress :percentage="100" type="dashboard" status="success" />
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
                <ks-progress :percentage="50" type="circle" status="exception" />
            </div>
        `,
    }),
}
