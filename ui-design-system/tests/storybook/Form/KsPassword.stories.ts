import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsPassword from "../../../src/components/Form/KsPassword.vue"

const meta: Meta<typeof KsPassword> = {
    title: "Components/Form/KsPassword",
    component: KsPassword,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        placeholder: {control: "text"},
    },
    parameters: {
        docs: {description: {component: "KsPassword is a multiline password input that masks its value using a custom disc font and provides a show/hide toggle button."}},
    },
}
export default meta
type Story = StoryObj<typeof KsPassword>

export const Default: Story = {
    render: (args) => ({
        components: {KsPassword},
        setup() {
            const value = ref("")
            return {args, value}
        },
        template: "<div style=\"padding:24px;width:400px\"><ks-password v-model=\"value\" v-bind=\"args\" /></div>",
    }),
    args: {placeholder: "Enter secret value..."},
}

export const WithValue: Story = {
    render: () => ({
        components: {KsPassword},
        setup() {
            return {value: ref("my-super-secret-api-key-1234")}
        },
        template: "<div style=\"padding:24px;width:400px\"><ks-password v-model=\"value\" placeholder=\"Secret value\" /></div>",
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsPassword},
        setup() {
            return {value: ref("hidden-secret-value")}
        },
        template: "<div style=\"padding:24px;width:400px\"><ks-password v-model=\"value\" disabled placeholder=\"Secret value\" /></div>",
    }),
}

export const MultilineValue: Story = {
    render: () => ({
        components: {KsPassword},
        setup() {
            return {value: ref("-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC\n-----END PRIVATE KEY-----")}
        },
        template: "<div style=\"padding:24px;width:400px\"><ks-password v-model=\"value\" placeholder=\"Paste your private key...\" /></div>",
    }),
}
