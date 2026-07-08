import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import CopilotComposer from "../../../../../src/components/ai/copilot/CopilotComposer.vue"
import en from "../../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})

const meta: Meta<typeof CopilotComposer> = {
    title: "ai/copilot/CopilotComposer",
    component: CopilotComposer,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: "<div style=\"width: 400px; padding: 12px;\"><story /></div>",
        }),
    ],
    argTypes: {
        mode: {control: "select", options: ["ASK", "EDIT", "PLAN"]},
        disabled: {control: "boolean"},
    },
}
export default meta
type Story = StoryObj<typeof CopilotComposer>

export const Ask: Story = {args: {mode: "ASK"}}
export const Build: Story = {args: {mode: "EDIT"}}
export const Plan: Story = {args: {mode: "PLAN"}}

// Disabled while a turn is streaming or awaiting confirmation.
export const Disabled: Story = {args: {mode: "ASK", disabled: true}}
