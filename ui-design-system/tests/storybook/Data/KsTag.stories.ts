import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsTag from "../../../src/components/Data/KsTag/KsTag.vue"

const meta: Meta<typeof KsTag> = {
    title: "Components/Data/KsTag",
    component: KsTag,
    tags: ["autodocs"],
    argTypes: {
        type: {control: "select", options: ["", "success", "info", "warning", "danger", "primary"]},
        size: {control: "select", options: ["large", "default", "small"]},
        effect: {control: "select", options: ["dark", "light", "plain"]},
        closable: {control: "boolean"},
        round: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsTag is the Kestra design-system abstraction over `ElTag` from Element Plus. Only the props, events and slots actually used across the Kestra UI are exposed."}},
    },
}
export default meta
type Story = StoryObj<typeof KsTag>

export const Default: Story = {
    render: (args) => ({
        components: {KsTag},
        setup() { return {args} },
        template: `<div style="padding:24px"><ks-tag v-bind="args">My Tag</ks-tag></div>`,
    }),
    args: {type: ""},
}

export const Types: Story = {
    render: () => ({
        components: {KsTag},
        template: `
            <div style="padding:24px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
                <ks-tag>Default</ks-tag>
                <ks-tag type="primary">Primary</ks-tag>
                <ks-tag type="success">Success</ks-tag>
                <ks-tag type="info">Info</ks-tag>
                <ks-tag type="warning">Warning</ks-tag>
                <ks-tag type="danger">Danger</ks-tag>
            </div>
        `,
    }),
}

export const Effects: Story = {
    render: () => ({
        components: {KsTag},
        template: `
            <div style="padding:24px;display:flex;gap:8px;flex-wrap:wrap">
                <ks-tag type="primary" effect="dark">Dark</ks-tag>
                <ks-tag type="primary" effect="light">Light</ks-tag>
                <ks-tag type="primary" effect="plain">Plain</ks-tag>
            </div>
        `,
    }),
}

export const Closable: Story = {
    render: () => ({
        components: {KsTag},
        setup() {
            const tags = ["Tag 1", "Tag 2", "Tag 3"]
            const visibleTags = {value: [...tags]}
            function removeTag(tag: string) {
                visibleTags.value = visibleTags.value.filter(t => t !== tag)
            }
            return {visibleTags, removeTag}
        },
        template: `
            <div style="padding:24px;display:flex;gap:8px;flex-wrap:wrap">
                <ks-tag
                    v-for="tag in visibleTags.value"
                    :key="tag"
                    closable
                    type="primary"
                    @close="removeTag(tag)"
                >{{ tag }}</ks-tag>
            </div>
        `,
    }),
}

/** Rounded tags */
export const Rounded: Story = {
    render: () => ({
        components: {KsTag},
        template: `
            <div style="padding:24px;display:flex;gap:8px;flex-wrap:wrap">
                <ks-tag round>Default</ks-tag>
                <ks-tag type="primary" round>Primary</ks-tag>
                <ks-tag type="success" round>Success</ks-tag>
                <ks-tag type="warning" round effect="dark">Warning</ks-tag>
                <ks-tag type="danger" round effect="plain">Danger</ks-tag>
            </div>
        `,
    }),
}

/** Edit dynamically – add and remove tags */
export const EditDynamically: Story = {
    render: () => ({
        components: {KsTag},
        setup() {
            const tags = ref(["Tag 1", "Tag 2", "Tag 3"])
            const inputVisible = ref(false)
            const inputValue = ref("")
            function handleClose(tag: string) {
                tags.value = tags.value.filter(t => t !== tag)
            }
            function addTag() {
                if (inputValue.value && !tags.value.includes(inputValue.value)) {
                    tags.value.push(inputValue.value)
                }
                inputValue.value = ""
                inputVisible.value = false
            }
            return {tags, inputVisible, inputValue, handleClose, addTag}
        },
        template: `
            <div style="padding:24px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
                <ks-tag
                    v-for="tag in tags"
                    :key="tag"
                    closable
                    type="primary"
                    @close="handleClose(tag)"
                >{{ tag }}</ks-tag>
                <template v-if="inputVisible">
                    <input
                        v-model="inputValue"
                        @keyup.enter="addTag"
                        @blur="addTag"
                        placeholder="New tag"
                        style="width:80px;padding:2px 6px;font-size:12px;border:1px solid #ddd;border-radius:4px"
                        autofocus
                    />
                </template>
                <button v-else @click="inputVisible = true" style="font-size:12px;cursor:pointer">+ New Tag</button>
            </div>
        `,
    }),
}

export const Sizes: Story = {
    render: () => ({
        components: {KsTag},
        template: `
            <div style="padding:24px;display:flex;gap:8px;align-items:center">
                <ks-tag type="primary" size="large">Large</ks-tag>
                <ks-tag type="primary">Default</ks-tag>
                <ks-tag type="primary" size="small">Small</ks-tag>
            </div>
        `,
    }),
}
