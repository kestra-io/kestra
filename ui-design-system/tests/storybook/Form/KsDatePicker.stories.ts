import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsDatePicker from "../../../src/components/Form/KsDatePicker.vue"

const meta: Meta<typeof KsDatePicker> = {
    title: "Components/Form/KsDatePicker",
    component: KsDatePicker,
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
    args: {type: "date", placeholder: "Select date"},
}

/** Week picker */
export const WeekPicker: Story = {
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
