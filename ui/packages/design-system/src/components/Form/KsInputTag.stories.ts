import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsInputTag from "./KsInputTag.vue"

const meta: Meta<typeof KsInputTag> = {
    title: "Components/Form/KsInputTag",
    component: KsInputTag,
    tags: ["autodocs"],
    argTypes: {
        disabled: {control: "boolean"},
        size: {control: "select", options: ["small", "default", "large"]},
        placeholder: {control: "text"},
    },
    parameters: {
        docs: {description: {component: "KsInputTag is the Kestra design-system abstraction over `ElInputTag` from Element Plus: one input where Enter turns the typed text into a removable tag. A `delimiter` also splits typed or pasted text into several tags, and `invalid` marks tags in the error tone without rejecting them."}},
    },
}
export default meta
type Story = StoryObj<typeof KsInputTag>

const render: Story["render"] = (args) => ({
    components: {KsInputTag},
    setup() {
        const value = ref<string[]>([...(args.modelValue ?? [])])
        return {args, value}
    },
    template: `
        <div style="padding:24px;max-width:480px">
            <ks-input-tag v-model="value" v-bind="args" />
            <span style="display:block;margin-top:8px;font-size:13px;opacity:0.6">Value: {{ JSON.stringify(value) }}</span>
        </div>
    `,
})

export const Default: Story = {
    render,
    args: {
        modelValue: ["grace@example.com", "linus@example.com"],
        placeholder: "Add an email and press Enter",
        size: "default",
        disabled: false,
    },
}

export const Empty: Story = {
    render,
    args: {placeholder: "Add an email and press Enter"},
}

export const PastedList: Story = {
    render,
    args: {
        placeholder: "Paste a list separated by commas, spaces or new lines",
        delimiter: /[\s,;]+/,
    },
}

export const InvalidTags: Story = {
    render,
    args: {
        modelValue: ["grace@example.com", "not-an-email", "linus@example.com"],
        invalid: (value: string) => !value.includes("@"),
        invalidLabel: "Not a valid email",
    },
}

export const ManyTags: Story = {
    render,
    args: {
        modelValue: Array.from({length: 14}, (_, index) => `user${index + 1}@example.com`),
    },
}

export const Disabled: Story = {
    render,
    args: {
        modelValue: ["grace@example.com"],
        disabled: true,
    },
}

export const Small: Story = {
    render,
    args: {
        modelValue: ["grace@example.com", "linus@example.com"],
        size: "small",
    },
}
