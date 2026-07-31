import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import FlowRecipe from "../../../../src/components/flows/recipe/FlowRecipe.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            recipe: {
                when: {title: "WHEN", subtitle: "Choose what triggers this system flow.", trigger_type: "Trigger type"},
                then: {title: "THEN", subtitle: "Select how to notify your team.", no_channel_warning: "Select at least one notification channel."},
                trigger: {
                    execution_title: "Execution status", execution_sub: "Reacts to flow state changes",
                    schedule_title: "Schedule", schedule_sub: "Runs on a time schedule",
                    case_title: "Case status", case_sub: "EE only feature",
                    webhook_title: "Webhook", webhook_sub: "Triggered by an HTTP request",
                    other_title: "Other trigger", other_sub: "Any available trigger plugin",
                },
                execution: {
                    watch_namespace: "Watch namespace", namespace_placeholder: "Select a namespace",
                    include_sub: "Include sub-namespaces", include_sub_hint_on: "Child namespaces trigger alerts.",
                    include_sub_hint_off: "Exact namespace only.", states: "On these states",
                    states_required: "Select at least one state.",
                },
                schedule: {
                    frequency: "Frequency", cron: "Cron expression", timezone: "Timezone",
                    timezone_placeholder: "Select timezone (default UTC)", daily: "Daily",
                    hourly: "Hourly", weekly: "Weekly", custom: "Custom",
                    daily_hint: "Runs daily at 9:00.", hourly_hint: "Runs hourly at minute 0.", weekly_hint: "Runs every Monday at 9:00.",
                },
                webhook: {
                    key_label: "Webhook key", key_placeholder: "Enter unique key",
                    endpoint_url: "Endpoint URL", endpoint_hint: "Send a POST request to this URL.",
                },
                other: {search_label: "Search triggers", search_placeholder: "Filter", no_results: "No triggers found."},
                notify: {
                    slack_sub: "Post a message to a Slack channel",
                    teams_sub: "Send a card to a Teams channel",
                    email_label: "Email", email_sub: "Send an email notification",
                    slack_channel_placeholder: "#alerts",
                    teams_webhook_placeholder: "Teams incoming webhook URL",
                    email_to_placeholder: "recipient@your-domain.com",
                    plugin_unavailable: "Plugin not installed",
                },
                summary: {
                    title: "Summary",
                    empty: "Configure the trigger and at least one notification channel to preview your flow.",
                    invalid_hint: "Add at least one notification channel and complete the trigger configuration.",
                    no_channel: "no channel configured",
                    any_namespace: "any namespace", including_sub: "and sub-namespaces",
                    exact_match: "exact match", selected_trigger: "selected trigger",
                    execution: "When a flow in namespace {ns} ({scope}) reaches state {states}, notify via {channels}.",
                    schedule: "On schedule \"{cron}\", notify via {channels}.",
                    webhook: "When a webhook is received, notify via {channels}.",
                    other: "When trigger \"{trigger}\" fires, notify via {channels}.",
                },
                create_flow: "Create flow",
                section_title: "Create a system flow",
                section_subtitle: "Monitor and notify your team automatically.",
            },
            email: "Email",
            copy: "Copy",
        },
    },
})

const pinia = createPinia()

const meta: Meta<typeof FlowRecipe> = {
    title: "flows/FlowRecipe",
    component: FlowRecipe,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem, pinia],
            template: "<div style=\"max-width: 1200px; padding: 16px;\"><story /></div>",
        }),
    ],
}

export default meta

export const Default: StoryObj<typeof FlowRecipe> = {
    args: {
        namespace: "system",
    },
}

export const WithExecutionTrigger: StoryObj<typeof FlowRecipe> = {
    args: {
        namespace: "system",
    },
    name: "Execution trigger panel",
}

export const DarkMode: StoryObj<typeof FlowRecipe> = {
    args: {
        namespace: "system",
    },
    parameters: {
        themes: {themeOverride: "dark"},
    },
}
