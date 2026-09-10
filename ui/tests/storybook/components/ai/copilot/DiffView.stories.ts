import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import DiffView from "../../../../../src/components/ai/copilot/DiffView.vue"
import en from "../../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})

const meta: Meta<typeof DiffView> = {
    title: "ai/copilot/DiffView",
    component: DiffView,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: "<div style=\"width: 480px; padding: 12px;\"><story /></div>",
        }),
    ],
}
export default meta
type Story = StoryObj<typeof DiffView>

const before = [
    "id: my-flow",
    "namespace: company.team",
    "tasks:",
    "  - id: log",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: Hello",
].join("\n")

// A mutating action that edits an existing flow: additions and removals side by side.
export const MixedChanges: Story = {
    args: {
        oldValue: before,
        newValue: [
            "id: my-flow",
            "namespace: company.team",
            "tasks:",
            "  - id: log",
            "    type: io.kestra.plugin.core.log.Log",
            "    message: Hello world",
            "  - id: notify",
            "    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook",
        ].join("\n"),
    },
}

// Nothing to diff against yet (e.g. creating a brand-new flow): every line renders as an addition.
export const PureAddition: Story = {
    args: {oldValue: "", newValue: before},
}

// The proposed content is identical to what's already there.
export const NoChanges: Story = {
    args: {oldValue: before, newValue: before},
}

// A large unrelated block of unchanged lines collapses to a gap marker around the actual edit.
export const CollapsedContext: Story = {
    args: {
        oldValue: Array.from({length: 20}, (_, i) => `line ${i}`).join("\n"),
        newValue: Array.from({length: 20}, (_, i) => (i === 10 ? "changed line" : `line ${i}`)).join("\n"),
    },
}
