import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsSwitch from "../../../src/components/Form/KsSwitch.vue"

const meta: Meta<typeof KsSwitch> = {
    title: "Components/Form/KsSwitch",
    component: KsSwitch,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
    },
    parameters: {
        docs: {description: {component: "KsSwitch is the Kestra design-system abstraction over `ElSwitch` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsSwitch>

export const Default: Story = {
    render: (args) => ({
        components: {KsSwitch},
        setup() {
            const value = ref(false)
            return {args, value}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-switch v-model="value" v-bind="args" />
                <span style="font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
}

export const WithLabels: Story = {
    render: () => ({
        components: {KsSwitch},
        setup() { return {value: ref(false)} },
        template: `
            <div style="padding:24px">
                <ks-switch v-model="value" active-text="Enabled" inactive-text="Disabled" />
            </div>
        `,
    }),
}

export const Sizes: Story = {
    render: () => ({
        components: {KsSwitch},
        setup() { return {v1: ref(true), v2: ref(true), v3: ref(true)} },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <ks-switch v-model="v1" size="large" />
                <ks-switch v-model="v2" />
                <ks-switch v-model="v3" size="small" />
            </div>
        `,
    }),
}

/** Extended value types – string/number active/inactive values */
export const ExtendedValues: Story = {
    render: () => ({
        components: {KsSwitch},
        setup() {
            const env = ref("production")
            const priority = ref(1)
            return {env, priority}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <div style="display:flex;align-items:center;gap:12px">
                    <ks-switch v-model="env" active-value="production" inactive-value="development" />
                    <span style="font-size:13px;opacity:0.7">{{ env }}</span>
                </div>
                <div style="display:flex;align-items:center;gap:12px">
                    <ks-switch v-model="priority" :active-value="1" :inactive-value="0" />
                    <span style="font-size:13px;opacity:0.7">Priority: {{ priority }}</span>
                </div>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsSwitch},
        setup() { return {value: ref(true)} },
        template: "<div style=\"padding:24px\"><ks-switch v-model=\"value\" disabled /></div>",
    }),
}
