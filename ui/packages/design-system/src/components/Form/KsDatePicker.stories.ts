import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref, type ConcreteComponent} from "vue"
import {expect, userEvent, waitFor} from "storybook/test"
import KsDatePicker from "./KsDatePicker.vue"

const meta: Meta<typeof KsDatePicker> = {
    title: "Components/Form/KsDatePicker",
    // A generic <script setup> component types as a bare function, which Storybook's component field does not accept.
    component: KsDatePicker as ConcreteComponent,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["date", "datetime", "daterange", "datetimerange", "week", "month", "year"]},
        disabled: {control: "boolean"},
        clearable: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
    },
    parameters: {
        docs: {description: {component: "KsDatePicker is the Kestra design-system abstraction over `ElDatePicker` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsDatePicker>

export const Default: Story = {
    render: (args) => ({
        components: {KsDatePicker},
        setup() {
            const value = ref(null)
            return {args, value}
        },
        template: `
            <div style="padding:24px;min-height:400px;display:flex;flex-direction:column;gap:12px">
                <ks-date-picker v-model="value" v-bind="args" />
                <span style="font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
    args: {
        type: "date",
        placeholder: "Select date",
        startPlaceholder: "Start date",
        endPlaceholder: "End date",
        size: "default",
        disabled: false,
        clearable: true,
        unlinkPanels: false,
    },
}

/** Week picker */
export const WeekPicker: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsDatePicker},
        setup() { return {value: ref(null)} },
        template: `
            <div style="padding:24px;min-height:400px">
                <ks-date-picker v-model="value" type="week" placeholder="Select week" />
            </div>
        `,
    }),
}

/** Month picker */
export const MonthPicker: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsDatePicker},
        setup() { return {value: ref(null)} },
        template: `
            <div style="padding:24px;min-height:400px">
                <ks-date-picker v-model="value" type="month" placeholder="Select month" clearable />
            </div>
        `,
    }),
}

/** Disabled dates */
export const DisabledDates: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsDatePicker},
        setup() {
            const value = ref(null)
            const disabledDate = (date: Date) => date.getTime() < Date.now() - 8.64e7
            return {value, disabledDate}
        },
        template: `
            <div style="padding:24px;min-height:400px">
                <ks-date-picker
                    v-model="value"
                    type="date"
                    placeholder="Future dates only"
                    :disabled-date="disabledDate"
                />
            </div>
        `,
    }),
}

/** Date + time (datetime type) */
export const DateTime: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsDatePicker},
        setup() { return {value: ref(null)} },
        template: `
            <div style="padding:24px;min-height:400px">
                <ks-date-picker v-model="value" type="datetime" placeholder="Select date and time" clearable />
            </div>
        `,
    }),
}

export const DateRange: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsDatePicker},
        setup() { return {value: ref(null)} },
        template: `
            <div style="padding:24px;min-height:400px">
                <ks-date-picker
                    v-model="value"
                    type="daterange"
                    start-placeholder="Start date"
                    end-placeholder="End date"
                    :unlink-panels="true"
                />
            </div>
        `,
    }),
}

/**
 * Clearable inside a shrink-to-fit container, as in the KsFilter date popover. The clear icon only
 * appears on hover, so the picker reserves its room: the width stays put instead of flickering.
 */
export const ClearableNoReflow: Story = {
    parameters: {controls: {disable: true}},
    render: () => ({
        components: {KsDatePicker},
        setup() { return {value: ref(new Date("2026-01-15T10:00:00Z"))} },
        template: `
            <div style="padding:24px;display:inline-flex;border:1px dashed var(--ks-border-default)">
                <KsDatePicker v-model="value" type="datetime" />
            </div>
        `,
    }),
    async play({canvasElement}) {
        const editor = canvasElement.querySelector<HTMLElement>(".kel-date-editor")!
        const widthBefore = editor.getBoundingClientRect().width

        await userEvent.hover(editor)
        await waitFor(() => expect(canvasElement.querySelector(".clear-icon")).not.toBeNull())

        await expect(editor.getBoundingClientRect().width).toBe(widthBefore)
    },
}
