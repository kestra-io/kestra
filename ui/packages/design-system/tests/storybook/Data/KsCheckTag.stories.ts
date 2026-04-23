import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {within, userEvent, expect} from "storybook/test"
import KsCheckTag from "../../../src/components/Data/KsTag/KsCheckTag.vue"

const meta: Meta<typeof KsCheckTag> = {
    title: "Components/Data/KsCheckTag",
    component: KsCheckTag,
    tags: ["autodocs"],
    argTypes: {
        checked: {control: "boolean"},
        disabled: {control: "boolean"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsCheckTag is the Kestra design-system abstraction over `ElCheckTag` from Element Plus. " +
                    "It is a checkable tag that toggles between checked and unchecked states.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsCheckTag>

/** Default check tag */
export const Default: Story = {
    render: (args) => ({
        components: {KsCheckTag},
        setup() {
            const checked = ref(args.checked ?? false)
            return {args, checked}
        },
        template: `
            <div style="padding:24px">
                <ks-check-tag v-model:checked="checked" v-bind="args">Option</ks-check-tag>
            </div>
        `,
    }),
    args: {checked: false},
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const tag = canvas.getByText("Option")
        await expect(tag).toBeTruthy()
        await userEvent.click(tag)
    },
}

/** Pre-checked state */
export const Checked: Story = {
    render: () => ({
        components: {KsCheckTag},
        template: `
            <div style="padding:24px;display:flex;gap:8px">
                <ks-check-tag :checked="false">Unchecked</ks-check-tag>
                <ks-check-tag :checked="true">Checked</ks-check-tag>
            </div>
        `,
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".kel-check-tag.is-checked")).toBeTruthy()
    },
}

/** Disabled state */
export const Disabled: Story = {
    render: () => ({
        components: {KsCheckTag},
        template: `
            <div style="padding:24px;display:flex;gap:8px">
                <ks-check-tag :checked="false" disabled>Disabled unchecked</ks-check-tag>
                <ks-check-tag :checked="true" disabled>Disabled checked</ks-check-tag>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const disabled = canvasElement.querySelectorAll(".kel-check-tag.is-disabled")
        await expect(disabled.length).toBe(2)
    },
}

/** Multi-select group */
export const MultiSelectGroup: Story = {
    render: () => ({
        components: {KsCheckTag},
        setup() {
            const selected = ref<string[]>(["flows"])
            const tags = ["flows", "executions", "namespaces", "triggers", "logs"]
            function toggle(tag: string) {
                const idx = selected.value.indexOf(tag)
                if (idx >= 0) selected.value.splice(idx, 1)
                else selected.value.push(tag)
            }
            return {tags, selected, toggle}
        },
        template: `
            <div style="padding:24px">
                <div style="display:flex;gap:8px;flex-wrap:wrap">
                    <ks-check-tag
                        v-for="tag in tags"
                        :key="tag"
                        :checked="selected.includes(tag)"
                        @change="toggle(tag)"
                    >{{ tag }}</ks-check-tag>
                </div>
                <p style="margin-top:12px;font-size:13px;opacity:0.6">
                    Selected: {{ selected.join(', ') || '(none)' }}
                </p>
            </div>
        `,
    }),
}
