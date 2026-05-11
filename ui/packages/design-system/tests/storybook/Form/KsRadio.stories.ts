import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsRadio from "../../../src/components/Form/KsRadio/KsRadio.vue"
import KsRadioGroup from "../../../src/components/Form/KsRadio/KsRadioGroup.vue"
import KsRadioButton from "../../../src/components/Form/KsRadio/KsRadioButton.vue"

const meta: Meta<typeof KsRadio> = {
    title: "Components/Form/KsRadio",
    component: KsRadio,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsRadio is the Kestra design-system abstraction over `ElRadio` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsRadio>

export const Default: Story = {
    render: (args) => ({
        components: {KsRadio},
        setup() {
            const value = ref("A")
            return {args, value}
        },
        template: `
            <div style="padding:24px;display:flex;gap:16px">
                <ks-radio v-model="value" value="A" v-bind="args">Option A</ks-radio>
                <ks-radio v-model="value" value="B" v-bind="args">Option B</ks-radio>
                <span style="font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
}

export const Group: Story = {
    render: () => ({
        components: {KsRadioGroup, KsRadio},
        setup() {
            const value = ref("daily")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <ks-radio-group v-model="value">
                    <ks-radio value="daily">Daily</ks-radio>
                    <ks-radio value="weekly">Weekly</ks-radio>
                    <ks-radio value="monthly">Monthly</ks-radio>
                </ks-radio-group>
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
}

export const ButtonGroup: Story = {
    render: () => ({
        components: {KsRadioGroup, KsRadioButton},
        setup() {
            const value = ref("left")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <ks-radio-group v-model="value">
                    <ks-radio-button value="left">Left</ks-radio-button>
                    <ks-radio-button value="center">Center</ks-radio-button>
                    <ks-radio-button value="right">Right</ks-radio-button>
                </ks-radio-group>
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
}

export const Sizes: Story = {
    render: () => ({
        components: {KsRadioGroup, KsRadioButton},
        setup() {
            return {v1: ref("a"), v2: ref("a"), v3: ref("a")}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px">
                <ks-radio-group v-model="v1" size="large">
                    <ks-radio-button value="a">Large A</ks-radio-button>
                    <ks-radio-button value="b">Large B</ks-radio-button>
                </ks-radio-group>
                <ks-radio-group v-model="v2">
                    <ks-radio-button value="a">Default A</ks-radio-button>
                    <ks-radio-button value="b">Default B</ks-radio-button>
                </ks-radio-group>
                <ks-radio-group v-model="v3" size="small">
                    <ks-radio-button value="a">Small A</ks-radio-button>
                    <ks-radio-button value="b">Small B</ks-radio-button>
                </ks-radio-group>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsRadioGroup, KsRadio},
        setup() { return {value: ref("B")} },
        template: `
            <div style="padding:24px">
                <ks-radio-group v-model="value" disabled>
                    <ks-radio value="A">Option A</ks-radio>
                    <ks-radio value="B">Option B</ks-radio>
                </ks-radio-group>
            </div>
        `,
    }),
}
