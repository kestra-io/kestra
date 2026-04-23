import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsInputNumber from "../../../src/components/Form/KsInputNumber.vue"

const meta: Meta<typeof KsInputNumber> = {
    title: "Components/Form/KsInputNumber",
    component: KsInputNumber,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
        controls: {control: "boolean"},
        controlsPosition: {control: "select", options: ["", "right"]},
    },
    parameters: {
        docs: {description: {component: "KsInputNumber is the Kestra design-system abstraction over `ElInputNumber` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsInputNumber>

export const Default: Story = {
    render: (args) => ({
        components: {KsInputNumber},
        setup() {
            const value = ref(1)
            return {args, value}
        },
        template: `
            <div style="padding:24px">
                <ks-input-number v-model="value" v-bind="args" />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
    args: {min: 0, max: 100},
}

export const WithStep: Story = {
    render: () => ({
        components: {KsInputNumber},
        setup() { return {value: ref(0)} },
        template: "<div style=\"padding:24px\"><ks-input-number v-model=\"value\" :step=\"5\" :min=\"0\" :max=\"100\" /></div>",
    }),
}

export const ControlsRight: Story = {
    render: () => ({
        components: {KsInputNumber},
        setup() { return {value: ref(10)} },
        template: "<div style=\"padding:24px\"><ks-input-number v-model=\"value\" controls-position=\"right\" :min=\"0\" :max=\"999\" /></div>",
    }),
}

/** Step strictly – only multiples of step are valid */
export const StepStrictly: Story = {
    render: () => ({
        components: {KsInputNumber},
        setup() { return {value: ref(0)} },
        template: `
            <div style="padding:24px">
                <ks-input-number v-model="value" :step="5" step-strictly :min="0" :max="100" />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Only multiples of 5</span>
            </div>
        `,
    }),
}

/** Precision – fixed decimal places */
export const Precision: Story = {
    render: () => ({
        components: {KsInputNumber},
        setup() { return {value: ref(3.14)} },
        template: `
            <div style="padding:24px">
                <ks-input-number v-model="value" :precision="2" :step="0.1" />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">2 decimal places</span>
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsInputNumber},
        setup() { return {value: ref(42)} },
        template: "<div style=\"padding:24px\"><ks-input-number v-model=\"value\" disabled /></div>",
    }),
}
