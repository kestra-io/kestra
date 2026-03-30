import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {within, expect} from "storybook/test"
import KsDurationPicker from "../../../src/components/Form/KsDurationPicker.vue"

const meta: Meta<typeof KsDurationPicker> = {
    title: "Components/Form/KsDurationPicker",
    component: KsDurationPicker,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsDurationPicker allows users to input an ISO 8601 duration (e.g. `P1Y2M3DT4H5M6S`) " +
                    "either via individual number fields for each unit, or by typing the duration string directly.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsDurationPicker>

/** Default – empty picker, all fields at zero */
export const Default: Story = {
    render: (args) => ({
        components: {KsDurationPicker},
        setup() {
            const value = ref<string | null>(null)
            return {args, value}
        },
        template: `
            <div style="padding:24px">
                <div style="display:flex;flex-wrap:wrap;gap:0.5rem">
                    <ks-duration-picker v-model="value" v-bind="args" />
                </div>
                <span style="display:block;margin-top:12px;font-size:13px;opacity:0.6">
                    Value: {{ value ?? '(null)' }}
                </span>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const customInput = canvas.getByRole("textbox")
        await expect(customInput).toBeTruthy()
    },
}

/** Pre-filled with an existing duration */
export const WithValue: Story = {
    render: () => ({
        components: {KsDurationPicker},
        setup() {
            const value = ref<string | null>("P1Y2M3DT4H30M")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <div style="display:flex;flex-wrap:wrap;gap:0.5rem">
                    <ks-duration-picker v-model="value" />
                </div>
                <span style="display:block;margin-top:12px;font-size:13px;opacity:0.6">
                    Value: {{ value ?? '(null)' }}
                </span>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const customInput = canvas.getByRole("textbox")
        await expect(customInput).toBeTruthy()
    },
}

/** Time-only duration (no date parts) */
export const TimeOnly: Story = {
    render: () => ({
        components: {KsDurationPicker},
        setup() {
            const value = ref<string | null>("PT1H30M")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <div style="display:flex;flex-wrap:wrap;gap:0.5rem">
                    <ks-duration-picker v-model="value" />
                </div>
                <span style="display:block;margin-top:12px;font-size:13px;opacity:0.6">
                    Value: {{ value ?? '(null)' }}
                </span>
            </div>
        `,
    }),
}

/** Date-only duration (no time parts) */
export const DateOnly: Story = {
    render: () => ({
        components: {KsDurationPicker},
        setup() {
            const value = ref<string | null>("P2Y6M15D")
            return {value}
        },
        template: `
            <div style="padding:24px">
                <div style="display:flex;flex-wrap:wrap;gap:0.5rem">
                    <ks-duration-picker v-model="value" />
                </div>
                <span style="display:block;margin-top:12px;font-size:13px;opacity:0.6">
                    Value: {{ value ?? '(null)' }}
                </span>
            </div>
        `,
    }),
}

/** Interactive – shows live binding with a form */
export const InForm: Story = {
    render: () => ({
        components: {KsDurationPicker},
        setup() {
            const value = ref<string | null>(null)
            const submitted = ref<string | null>(null)

            function submit() {
                submitted.value = value.value
            }

            return {value, submitted, submit}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:16px;max-width:600px">
                <label style="font-weight:600">Retention period</label>
                <div style="display:flex;flex-wrap:wrap;gap:0.5rem">
                    <ks-duration-picker v-model="value" />
                </div>
                <div style="display:flex;gap:8px;align-items:center">
                    <ks-button type="primary" @click="submit">Apply</ks-button>
                    <span style="font-size:13px;opacity:0.6">{{ submitted ? 'Submitted: ' + submitted : 'Not submitted yet' }}</span>
                </div>
            </div>
        `,
    }),
}
