import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsColorPicker from "../../../src/components/Form/KsColorPicker.vue"

const meta: Meta<typeof KsColorPicker> = {
    title: "Components/Form/KsColorPicker",
    component: KsColorPicker,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        showAlpha: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
    },
    parameters: {
        docs: {description: {component: "KsColorPicker is the Kestra design-system abstraction over `ElColorPicker` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsColorPicker>

export const Default: Story = {
    render: (args) => ({
        components: {KsColorPicker},
        setup() {
            const value = ref("#409EFF")
            return {args, value}
        },
        template: `
            <div style="padding:24px;display:flex;align-items:center;gap:16px">
                <ks-color-picker v-model="value" v-bind="args" />
                <span style="font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
}

export const WithAlpha: Story = {
    render: () => ({
        components: {KsColorPicker},
        setup() { return {value: ref("rgba(64, 158, 255, 0.5)")} },
        template: `
            <div style="padding:24px;display:flex;align-items:center;gap:16px">
                <ks-color-picker v-model="value" :show-alpha="true" />
                <span style="font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
}

export const WithPredefine: Story = {
    render: () => ({
        components: {KsColorPicker},
        setup() {
            return {
                value: ref("#ff4500"),
                predefine: ["#ff4500", "#ff8c00", "#ffd700", "#90ee90", "#00ced1", "#1e90ff", "#c71585"],
            }
        },
        template: `
            <div style="padding:24px;display:flex;align-items:center;gap:16px">
                <ks-color-picker v-model="value" :predefine="predefine" />
                <span style="font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsColorPicker},
        setup() { return {value: ref("#409EFF")} },
        template: "<div style=\"padding:24px\"><ks-color-picker v-model=\"value\" disabled /></div>",
    }),
}
