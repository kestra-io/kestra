import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {within, expect} from "storybook/test"
import KsTimePicker from "../../../src/components/Form/KsTimePicker.vue"

const meta: Meta<typeof KsTimePicker> = {
    title: "Components/Form/KsTimePicker",
    component: KsTimePicker,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        clearable: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
        placeholder: {control: "text"},
        format: {control: "text"},
        valueFormat: {control: "text"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsTimePicker is the Kestra design-system abstraction over `ElTimePicker` from Element Plus. " +
                    "Only the props and events actually used across the Kestra UI are exposed.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsTimePicker>

/** Default time picker */
export const Default: Story = {
    render: (args) => ({
        components: {KsTimePicker},
        setup() {
            const value = ref<Date | null>(null)
            return {args, value}
        },
        template: `
            <div style="padding:24px;width:240px">
                <ks-time-picker v-model="value" v-bind="args" />
            </div>
        `,
    }),
    args: {placeholder: "Select time", clearable: true},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const input = canvas.getByRole("combobox")
        await expect(input).toBeTruthy()
    },
}

/** Disabled state */
export const Disabled: Story = {
    render: () => ({
        components: {KsTimePicker},
        template: `
            <div style="padding:24px;width:240px">
                <ks-time-picker disabled placeholder="Not available" />
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const input = canvas.getByRole("combobox")
        await expect(input).toBeDisabled()
    },
}

/** All sizes */
export const Sizes: Story = {
    render: () => ({
        components: {KsTimePicker},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:240px">
                <ks-time-picker size="large" placeholder="Large" />
                <ks-time-picker placeholder="Default" />
                <ks-time-picker size="small" placeholder="Small" />
            </div>
        `,
    }),
}

/** With value format – emits ISO string instead of Date */
export const ValueFormat: Story = {
    render: () => ({
        components: {KsTimePicker},
        setup() {
            const value = ref<string>("")
            return {value}
        },
        template: `
            <div style="padding:24px;width:240px">
                <ks-time-picker
                    v-model="value"
                    format="HH:mm"
                    value-format="HH:mm:ss"
                    placeholder="Pick a time"
                    clearable
                />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">
                    Value: {{ value || '(none)' }}
                </span>
            </div>
        `,
    }),
}
