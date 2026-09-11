import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsThemePicker from "../../../src/components/Form/KsThemePicker/KsThemePicker.vue"

const OPTIONS = [
    {value: "dark-2", label: "Dark 2.0", preview: "dark-2" as const},
    {value: "dark", label: "Dark 1.0", preview: "dark" as const},
    {value: "light", label: "Light", preview: "light" as const},
    {value: "sync", label: "Sync with system", preview: "sync" as const},
]

const meta: Meta<typeof KsThemePicker> = {
    title: "Components/Form/KsThemePicker",
    component: KsThemePicker,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsThemePicker picks one of the themes the design system ships, each shown as a miniature of the app painted in that theme's own colours. The previews keep their colours whatever theme the page itself is in."}},
    },
}
export default meta
type Story = StoryObj<typeof KsThemePicker>

export const Default: Story = {
    render: () => ({
        components: {KsThemePicker},
        setup() {
            const value = ref("light")
            return {value, options: OPTIONS}
        },
        template: `
            <div style="padding:24px;max-width:640px">
                <ks-theme-picker v-model="value" :options="options" />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
}

export const DarkSelected: Story = {
    render: () => ({
        components: {KsThemePicker},
        setup() {
            const value = ref("dark")
            return {value, options: OPTIONS}
        },
        template: `
            <div style="padding:24px;max-width:640px">
                <ks-theme-picker v-model="value" :options="options" />
            </div>
        `,
    }),
}

export const TwoOptions: Story = {
    render: () => ({
        components: {KsThemePicker},
        setup() {
            const value = ref("dark")
            const options = OPTIONS.filter((option) => option.value === "dark" || option.value === "light")
            return {value, options}
        },
        template: `
            <div style="padding:24px;max-width:320px">
                <ks-theme-picker v-model="value" :options="options" />
            </div>
        `,
    }),
}

export const LongLabels: Story = {
    render: () => ({
        components: {KsThemePicker},
        setup() {
            const value = ref("sync")
            const options = OPTIONS.map((option) => ({...option, label: `${option.label} appearance`}))
            return {value, options}
        },
        template: `
            <div style="padding:24px;max-width:520px">
                <ks-theme-picker v-model="value" :options="options" />
            </div>
        `,
    }),
}
