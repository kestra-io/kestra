import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import CopilotMessage from "../../../../../src/components/ai/copilot/CopilotMessage.vue"
import en from "../../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})

const meta: Meta<typeof CopilotMessage> = {
    title: "ai/copilot/CopilotMessage",
    component: CopilotMessage,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: "<div style=\"width: 380px; padding: 12px;\"><story /></div>",
        }),
    ],
}
export default meta
type Story = StoryObj<typeof CopilotMessage>

export const UserPrompt: Story = {
    args: {message: {id: "1", role: "USER", type: "TEXT", content: "Add error handling to my dbt flow."}},
}

export const UserPromptWithCode: Story = {
    args: {
        message: {
            id: "1c",
            role: "USER",
            type: "TEXT",
            content: "This flow fails on start:\n```yaml\nid: hello\nnamespace: company.team\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: Hello\n```\nAny idea why?",
        },
    },
}

export const AssistantMarkdown: Story = {
    args: {message: {id: "2", role: "ASSISTANT", type: "TEXT", content: "Sure — I'll add a `errors:` block with a **Slack** alert."}},
}

export const ToolCall: Story = {
    args: {message: {id: "3", role: "TOOL", type: "TOOL_CALL", toolCall: {tool: "read-execution", family: "READ", arguments: {executionId: "exec-42", withLogs: true}}}},
}

export const ToolResultOk: Story = {
    args: {message: {id: "4", role: "TOOL", type: "TOOL_RESULT", toolResult: {tool: "restart-execution", outcome: "ok"}}},
}

export const ToolResultRejected: Story = {
    args: {message: {id: "5", role: "TOOL", type: "TOOL_RESULT", toolResult: {tool: "restart-execution", outcome: "rejected"}}},
}
