import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import LockOutline from "vue-material-design-icons/LockOutline.vue"
import KsRadioCardGroup from "../../../src/components/Form/KsRadio/KsRadioCardGroup.vue"

const meta: Meta<typeof KsRadioCardGroup> = {
    title: "Components/Form/KsRadioCardGroup",
    component: KsRadioCardGroup,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsRadioCardGroup renders a single-select radio group as bordered option cards, each with a title and an optional hint, icon, and disabled state."}},
    },
}
export default meta
type Story = StoryObj<typeof KsRadioCardGroup>

export const Default: Story = {
    render: () => ({
        components: {KsRadioCardGroup},
        setup() {
            const value = ref("SERVER")
            const options = [
                {value: "SERVER", label: "Server", hint: "This instance deploys with a shared token"},
                {value: "CLIENT", label: "Client", hint: "Your browser deploys with your own token"},
            ]
            return {value, options}
        },
        template: `
            <div style="padding:24px;max-width:520px">
                <ks-radio-card-group v-model="value" :options="options" ariaLabel="Connection mode" />
                <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Selected: {{ value }}</span>
            </div>
        `,
    }),
}

export const WithIcon: Story = {
    render: () => ({
        components: {KsRadioCardGroup},
        setup() {
            const value = ref("BASIC")
            const options = [
                {value: "BASIC", label: "Basic auth", hint: "Username & password"},
                {value: "API_TOKEN", label: "API token", hint: "Bearer token", icon: LockOutline},
                {value: "OAUTH", label: "OAuth", hint: "Sign in with a provider", icon: LockOutline},
            ]
            return {value, options}
        },
        template: `
            <div style="padding:24px;max-width:520px">
                <ks-radio-card-group v-model="value" :options="options" />
            </div>
        `,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsRadioCardGroup},
        setup() {
            const value = ref("BASIC")
            const options = [
                {value: "BASIC", label: "Basic auth", hint: "Username & password"},
                {value: "API_TOKEN", label: "API token", hint: "Enterprise only", disabled: true, icon: LockOutline},
            ]
            return {value, options}
        },
        template: `
            <div style="padding:24px;max-width:520px">
                <ks-radio-card-group v-model="value" :options="options" />
            </div>
        `,
    }),
}

export const LongText: Story = {
    render: () => ({
        components: {KsRadioCardGroup},
        setup() {
            const value = ref("a")
            const options = [
                {value: "a", label: "A concise option", hint: "A much longer hint that explains the trade-offs of this option in more detail than usually fits on a single line"},
                {value: "b", label: "Another option"},
            ]
            return {value, options}
        },
        template: `
            <div style="padding:24px;max-width:520px">
                <ks-radio-card-group v-model="value" :options="options" />
            </div>
        `,
    }),
}
