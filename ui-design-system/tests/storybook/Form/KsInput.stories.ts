import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsInput from "../../../src/components/Form/KsInput.vue"

const meta: Meta<typeof KsInput> = {
    title: "Components/Form/KsInput",
    component: KsInput,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["text", "password", "textarea"]},
        size: {control: "select", options: ["small", "default", "large"]},
        disabled: {control: "boolean"},
        clearable: {control: "boolean"},
        showPassword: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsInput is the Kestra design-system abstraction over `ElInput` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsInput>

export const Default: Story = {
    render: (args) => ({
        components: {KsInput},
        setup() {
            const value = ref("")
            return {args, value}
        },
        template: `<div style="padding:24px;width:300px"><ks-input v-model="value" v-bind="args" /></div>`,
    }),
    args: {placeholder: "Type something..."},
}

export const Sizes: Story = {
    render: () => ({
        components: {KsInput},
        setup() {
            return {v1: ref(""), v2: ref(""), v3: ref("")}
        },
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px;width:300px">
                <ks-input v-model="v1" size="large" placeholder="Large" />
                <ks-input v-model="v2" placeholder="Default" />
                <ks-input v-model="v3" size="small" placeholder="Small" />
            </div>
        `,
    }),
}

export const Password: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("")} },
        template: `<div style="padding:24px;width:300px"><ks-input v-model="value" type="password" :show-password="true" placeholder="Enter password" /></div>`,
    }),
}

export const TextArea: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("")} },
        template: `<div style="padding:24px;width:300px"><ks-input v-model="value" type="textarea" :rows="4" placeholder="Enter text..." /></div>`,
    }),
}

export const Disabled: Story = {
    render: () => ({
        components: {KsInput},
        setup() { return {value: ref("Disabled value")} },
        template: `<div style="padding:24px;width:300px"><ks-input v-model="value" disabled /></div>`,
    }),
}
