import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsSegmented from "../../../src/components/Data/KsSegmented.vue"

const meta: Meta<typeof KsSegmented> = {
    title: "Components/Data/KsSegmented",
    component: KsSegmented,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        block: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
    },
    parameters: {
        docs: {description: {component: "KsSegmented is the Kestra design-system abstraction over `ElSegmented` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsSegmented>

export const Default: Story = {
    render: (args) => ({
        components: {KsSegmented},
        setup() {
            const value = ref("daily")
            const options = ["daily", "weekly", "monthly"]
            return {args, value, options}
        },
        template: `
            <div style="padding:24px">
                <ks-segmented v-model="value" :options="options" v-bind="args" />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
}

export const WithObjectOptions: Story = {
    render: () => ({
        components: {KsSegmented},
        setup() {
            const value = ref("list")
            const options = [
                {label: "List View", value: "list"},
                {label: "Grid View", value: "grid"},
                {label: "Table View", value: "table", disabled: true},
            ]
            return {value, options}
        },
        template: `
            <div style="padding:24px">
                <ks-segmented v-model="value" :options="options" />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
}

export const Sizes: Story = {
    render: () => ({
        components: {KsSegmented},
        setup() {
            return {v1: ref("a"), v2: ref("a"), v3: ref("a")}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <ks-segmented v-model="v1" size="large" :options="['a', 'b', 'c']" />
                <ks-segmented v-model="v2" :options="['a', 'b', 'c']" />
                <ks-segmented v-model="v3" size="small" :options="['a', 'b', 'c']" />
            </div>
        `,
    }),
}

/** Disabled – entire control or individual options */
export const Disabled: Story = {
    render: () => ({
        components: {KsSegmented},
        setup() {
            const v1 = ref("b")
            const v2 = ref("list")
            const partialOptions = [
                {label: "List", value: "list"},
                {label: "Grid", value: "grid", disabled: true},
                {label: "Table", value: "table"},
            ]
            return {v1, v2, partialOptions}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 6px">Fully disabled</p>
                    <ks-segmented v-model="v1" :options="['a', 'b', 'c']" disabled />
                </div>
                <div>
                    <p style="font-size:12px;opacity:0.5;margin:0 0 6px">Single option disabled</p>
                    <ks-segmented v-model="v2" :options="partialOptions" />
                </div>
            </div>
        `,
    }),
}

export const Block: Story = {
    render: () => ({
        components: {KsSegmented},
        setup() { return {value: ref("b")} },
        template: `
            <div style="padding:24px;width:300px">
                <ks-segmented v-model="value" :options="['a', 'b', 'c']" block />
            </div>
        `,
    }),
}
