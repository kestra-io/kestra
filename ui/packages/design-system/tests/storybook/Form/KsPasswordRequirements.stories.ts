import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsPasswordRequirements from "../../../src/components/Form/KsPasswordRequirements.vue"
import KsPassword from "../../../src/components/Form/KsPassword.vue"

const meta: Meta<typeof KsPasswordRequirements> = {
    title: "Components/Form/KsPasswordRequirements",
    component: KsPasswordRequirements,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "Live checklist of the basic-auth password policy. Pass the password value via `password`; reads validity out via `v-model:valid`."}},
    },
}
export default meta
type Story = StoryObj<typeof KsPasswordRequirements>

export const Default: Story = {
    render: () => ({
        components: {KsPasswordRequirements, KsPassword},
        setup() {
            return {password: ref(""), valid: ref(false)}
        },
        template: `
            <div style="max-width:320px;display:flex;flex-direction:column;gap:12px">
                <ks-password v-model="password" placeholder="Type a password" />
                <ks-password-requirements :password="password" v-model:valid="valid" />
                <small>valid: {{ valid }}</small>
            </div>
        `,
    }),
}

export const AllMet: Story = {
    render: () => ({
        components: {KsPasswordRequirements},
        template: "<ks-password-requirements password=\"StrongPass1\" />",
    }),
}

export const CustomRules: Story = {
    render: () => ({
        components: {KsPasswordRequirements},
        setup() {
            const rules = [
                {key: "min", label: "At least 12 characters", test: (p: string) => p.length >= 12},
                {key: "special", label: "One special character (!@#$%^&*)", test: (p: string) => /[!@#$%^&*]/.test(p)},
            ]
            return {rules}
        },
        template: "<ks-password-requirements password=\"Abcdefghijk!\" :rules=\"rules\" />",
    }),
}
