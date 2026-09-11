import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import ProposedActionCard from "../../../../../src/components/ai/copilot/ProposedActionCard.vue"
import en from "../../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})

const meta: Meta<typeof ProposedActionCard> = {
    title: "ai/copilot/ProposedActionCard",
    component: ProposedActionCard,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: "<div style=\"width: 400px; padding: 12px;\"><story /></div>",
        }),
    ],
}
export default meta
type Story = StoryObj<typeof ProposedActionCard>

// Plan-mode card: no concrete tool → title + "Pending approval" + numbered steps + "Approve & execute".
export const PlanCard: Story = {
    args: {action: {
        confirmationId: "c1",
        tool: null,
        title: "Add test coverage",
        summary: "Plan test coverage for this flow with mocked external tasks",
        steps: [
            {title: "Mock external task outputs (git, OpenAI, Slack)", detail: "tests/ai-summarize-weekly.test.yml"},
            {title: "Fixture realistic commit data for the summarizer", detail: "tests/ai-summarize-weekly.test.yml"},
            {title: "Assert the Slack notification fires on success", detail: "tests/ai-summarize-weekly.test.yml"},
        ],
    }},
}

// Edit-mode card: a concrete mutating tool → "Proposed action" + "Approve" + family tag.
export const ActionCard: Story = {
    args: {action: {confirmationId: "c2", tool: "restart-execution", family: "MUTATE", summary: "Run `restart-execution` with {id: exec-42}", arguments: {id: "exec-42"}}},
}

// While a decision is being sent, the card's controls are disabled.
export const Disabled: Story = {
    args: {action: {confirmationId: "c3", tool: "restart-execution", family: "MUTATE", summary: "Restart exec-42"}, disabled: true},
}

// A mutating action carrying the new flow YAML: shown as a diff instead of a raw arg, since there's
// no flow currently open to diff against, it renders as a pure addition.
export const ActionCardWithDiff: Story = {
    args: {action: {
        confirmationId: "c4",
        tool: "create-flow",
        family: "MUTATE",
        summary: "Create the flow `company.team.my-flow`",
        arguments: {
            namespace: "company.team",
            flowId: "my-flow",
            body: "id: my-flow\nnamespace: company.team\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: Hello",
        },
    }},
}

// Same action, but a flow is currently open under the same namespace/id — the diff compares against
// its live content, so additions and removals both show.
export const ActionCardWithDiffAgainstCurrentFlow: Story = {
    args: {
        action: {
            confirmationId: "c5",
            tool: "update-flow",
            family: "MUTATE",
            summary: "Update the flow `company.team.my-flow`",
            arguments: {
                namespace: "company.team",
                flowId: "my-flow",
                body: "id: my-flow\nnamespace: company.team\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: Hello world",
            },
        },
        currentFlowSource: "id: my-flow\nnamespace: company.team\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: Hello",
    },
}
