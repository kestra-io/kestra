import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {expect, userEvent} from "storybook/test"
import KsRangeSlider from "../../../src/components/Form/KsRangeSlider.vue"

const meta: Meta<typeof KsRangeSlider> = {
    title: "Components/Form/KsRangeSlider",
    component: KsRangeSlider,
    tags: ["autodocs"],
    argTypes: {
        min: {control: "number"},
        max: {control: "number"},
        minRange: {control: "number"},
        step: {control: "number"},
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "A drag-to-select range slider over a numeric domain: two resizable handles plus a draggable body to pan. `change` fires once a drag or keyboard nudge finishes, mirroring a native `<input type=\"range\">`'s `input`/`change` split."}},
    },
}
export default meta
type Story = StoryObj<typeof KsRangeSlider>

export const Default: Story = {
    render: (args) => ({
        components: {KsRangeSlider},
        setup() {
            const value = ref<[number, number]>([20, 80])
            return {args, value}
        },
        template: `
            <div style="padding:24px;width:400px;display:flex;flex-direction:column;gap:12px">
                <ks-range-slider v-model="value" v-bind="args" startLabel="Start" endLabel="End" />
                <span style="font-size:13px;opacity:0.6">Value: {{ value }}</span>
            </div>
        `,
    }),
    args: {
        min: 0,
        max: 100,
    },
}

export const TimeDomain: Story = {
    render: () => ({
        components: {KsRangeSlider},
        setup() {
            const now = Date.now()
            const min = now - 24 * 60 * 60 * 1000
            const value = ref<[number, number]>([now - 6 * 60 * 60 * 1000, now])
            const formatValue = (ms: number) => new Date(ms).toLocaleTimeString()
            return {min, max: now, value, formatValue}
        },
        template: `
            <div style="padding:24px;width:400px">
                <ks-range-slider v-model="value" :min="min" :max="max" :formatValue="formatValue" startLabel="Range start" endLabel="Range end" />
            </div>
        `,
    }),
}

export const Dragging: Story = {
    render: () => ({
        components: {KsRangeSlider},
        setup() {
            const value = ref<[number, number]>([20, 80])
            return {value, formatValue: (v: number) => String(Math.round(v))}
        },
        template: `
            <div style="padding:24px;width:400px">
                <ks-range-slider v-model="value" :min="0" :max="100" :formatValue="formatValue" startLabel="Start" endLabel="End" />
            </div>
        `,
    }),
    async play({canvasElement}) {
        const handle = canvasElement.querySelector("[data-test='range-slider-handle-end']") as HTMLElement

        await userEvent.pointer([
            {keys: "[MouseLeft>]", target: handle},
            {coords: {x: 100, y: 0}},
        ])
        await expect(canvasElement.querySelector(".ks-range-slider-value-end")).toBeTruthy()
        await userEvent.pointer({keys: "[/MouseLeft]"})
    },
}

export const Disabled: Story = {
    render: () => ({
        components: {KsRangeSlider},
        setup() { return {value: ref<[number, number]>([20, 80])} },
        template: "<div style=\"padding:24px;width:400px\"><ks-range-slider v-model=\"value\" :min=\"0\" :max=\"100\" startLabel=\"Start\" endLabel=\"End\" disabled /></div>",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-range-slider.is-disabled")).toBeTruthy()
    },
}
