import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {within, expect} from "storybook/test"
import KsCascaderPanel from "../../../src/components/Form/KsCascaderPanel.vue"

const baseOptions = [
    {
        value: "guide",
        label: "Guide",
        children: [
            {value: "disciplines", label: "Disciplines"},
            {value: "consistency", label: "Consistency"},
        ],
    },
    {
        value: "component",
        label: "Component",
        children: [
            {value: "basic", label: "Basic"},
            {value: "form", label: "Form"},
            {value: "data", label: "Data"},
        ],
    },
    {
        value: "resource",
        label: "Resource",
        children: [
            {value: "axure", label: "Axure Components"},
            {value: "sketch", label: "Sketch Templates"},
        ],
    },
]

const meta: Meta<typeof KsCascaderPanel> = {
    title: "Components/Form/KsCascaderPanel",
    component: KsCascaderPanel,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "KsCascaderPanel is the Kestra design-system abstraction over `ElCascaderPanel` from Element Plus. " +
                    "It renders an always-visible cascading selection panel.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsCascaderPanel>

/** Default panel with two-level options */
export const Default: Story = {
    render: () => ({
        components: {KsCascaderPanel},
        setup() {
            const value = ref<string[]>([])
            return {value, options: baseOptions}
        },
        template: `
            <div style="padding:24px">
                <ks-cascader-panel v-model="value" :options="options" />
                <p style="margin-top:12px;font-size:13px;opacity:0.6">
                    Selected: {{ value.length ? value.join(' / ') : '(none)' }}
                </p>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await expect(canvas.getByText("Guide")).toBeTruthy()
        await expect(canvas.getByText("Component")).toBeTruthy()
    },
}

/** Pre-selected value */
export const PreSelected: Story = {
    render: () => ({
        components: {KsCascaderPanel},
        setup() {
            const value = ref(["component", "form"])
            return {value, options: baseOptions}
        },
        template: `
            <div style="padding:24px">
                <ks-cascader-panel v-model="value" :options="options" />
                <p style="margin-top:12px;font-size:13px;opacity:0.6">
                    Selected: {{ value.join(' / ') }}
                </p>
            </div>
        `,
    }),
}

/** Three-level deep options */
export const ThreeLevels: Story = {
    render: () => ({
        components: {KsCascaderPanel},
        setup() {
            const value = ref<string[]>([])
            const options = [
                {
                    value: "eu",
                    label: "Europe",
                    children: [
                        {
                            value: "fr",
                            label: "France",
                            children: [
                                {value: "paris", label: "Paris"},
                                {value: "lyon", label: "Lyon"},
                            ],
                        },
                        {
                            value: "de",
                            label: "Germany",
                            children: [
                                {value: "berlin", label: "Berlin"},
                                {value: "munich", label: "Munich"},
                            ],
                        },
                    ],
                },
                {
                    value: "us",
                    label: "United States",
                    children: [
                        {
                            value: "ca",
                            label: "California",
                            children: [
                                {value: "sf", label: "San Francisco"},
                                {value: "la", label: "Los Angeles"},
                            ],
                        },
                    ],
                },
            ]
            return {value, options}
        },
        template: `
            <div style="padding:24px">
                <ks-cascader-panel v-model="value" :options="options" />
                <p style="margin-top:12px;font-size:13px;opacity:0.6">
                    Selected: {{ value.length ? value.join(' → ') : '(none)' }}
                </p>
            </div>
        `,
    }),
}
