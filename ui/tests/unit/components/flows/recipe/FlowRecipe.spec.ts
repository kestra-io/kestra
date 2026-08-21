import {describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {systemNamespace: "system"}}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: null}),
}))

vi.mock("../../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        ensurePlugins: vi.fn().mockResolvedValue([]),
        listTriggers: vi.fn().mockResolvedValue([]),
        ensureGroupIcons: vi.fn().mockResolvedValue({}),
    }),
}))

vi.mock("../../../../../src/composables/useNamespaces", () => ({
    default: () => ({all: vi.fn().mockResolvedValue([])}),
    defaultNamespace: () => undefined,
}))

vi.mock("@kestra-io/design-system", () => ({
    STATES: {
        FAILED: {icon: "div", color: "red"},
        WARNING: {icon: "div", color: "orange"},
        SUCCESS: {icon: "div", color: "green"},
        KILLED: {icon: "div", color: "gray"},
        PAUSED: {icon: "div", color: "blue"},
    },
    KsEditor: {template: "<div />"},
    stringUtils: {afterLastDot: (s: string) => s.split(".").pop() ?? s},
}))

vi.mock("../../../../../src/components/plugins/TaskIcon.vue", () => ({
    default: {template: "<div />"},
}))

vi.mock("@kestra-io/topology", () => ({
    flowYamlUtils: {
        stringify: (obj: unknown) => JSON.stringify(obj),
        parse: (s: string) => JSON.parse(s),
    },
}))

const messages = {
    en: {
        "recipe.when.title": "WHEN",
        "recipe.when.subtitle": "Choose what triggers this system flow.",
        "recipe.when.trigger_type": "Trigger type",
        "recipe.then.title": "THEN",
        "recipe.then.subtitle": "Select how to notify your team.",
        "recipe.then.no_channel_warning": "Select at least one notification channel.",
        "recipe.trigger.execution_title": "Execution status",
        "recipe.trigger.execution_sub": "Reacts to flow state changes",
        "recipe.trigger.schedule_title": "Schedule",
        "recipe.trigger.schedule_sub": "Runs on a time schedule",
        "recipe.trigger.case_title": "Case status",
        "recipe.trigger.case_sub": "EE only feature",
        "recipe.trigger.webhook_title": "Webhook",
        "recipe.trigger.webhook_sub": "Triggered by an HTTP request",
        "recipe.trigger.other_title": "Other trigger",
        "recipe.trigger.other_sub": "Any available trigger plugin",
        "recipe.summary.title": "Summary",
        "recipe.summary.empty": "Configure the trigger and at least one notification channel to preview your flow.",
        "recipe.summary.invalid_hint": "Add at least one notification channel and complete the trigger configuration.",
        "recipe.summary.no_channel": "no channel configured",
        "recipe.summary.any_namespace": "any namespace",
        "recipe.summary.including_sub": "and sub-namespaces",
        "recipe.summary.exact_match": "exact match",
        "recipe.summary.selected_trigger": "selected trigger",
        "recipe.summary.execution": "When a flow in namespace {ns} ({scope}) reaches state {states}, notify via {channels}.",
        "recipe.summary.schedule": "On schedule \"{cron}\", notify via {channels}.",
        "recipe.summary.webhook": "When a webhook is received, notify via {channels}.",
        "recipe.summary.other": "When trigger \"{trigger}\" fires, notify via {channels}.",
        "recipe.execution.watch_namespace": "Watch namespace",
        "recipe.execution.namespace_placeholder": "Select a namespace",
        "recipe.execution.include_sub": "Include sub-namespaces",
        "recipe.execution.include_sub_hint_on": "Child namespaces included.",
        "recipe.execution.include_sub_hint_off": "Exact namespace only.",
        "recipe.execution.states": "On these states",
        "recipe.execution.states_required": "Select at least one state.",
        "recipe.notify.slack_sub": "Post a message to a Slack channel",
        "recipe.notify.teams_sub": "Send a card to a Teams channel",
        "recipe.notify.email_label": "Email",
        "recipe.notify.email_sub": "Send an email notification",
        "recipe.notify.slack_channel_placeholder": "#alerts",
        "recipe.notify.teams_webhook_placeholder": "Teams incoming webhook URL",
        "recipe.notify.email_to_placeholder": "recipient{'@'}your-domain.com",
        "recipe.notify.custom_label": "Custom",
        "recipe.notify.custom_sub": "Add your own notification task",
        "recipe.notify.custom_note": "A placeholder task is added.",
        "recipe.notify.plugin_unavailable": "Plugin not installed",
        "recipe.create_flow": "Create flow",
        "recipe.section_title": "Create a system flow",
        "recipe.section_subtitle": "Monitor and notify.",
        "recipe.other.search_label": "Search triggers",
        "recipe.other.search_placeholder": "Filter",
        "recipe.other.no_results": "No triggers found.",
        "recipe.webhook.key_label": "Webhook key",
        "recipe.webhook.key_placeholder": "Enter unique key",
        "recipe.webhook.endpoint_url": "Endpoint URL",
        "recipe.webhook.endpoint_hint": "Send a POST request to this URL.",
        "recipe.schedule.frequency": "Frequency",
        "recipe.schedule.cron": "Cron expression",
        "recipe.schedule.timezone": "Timezone",
        "recipe.schedule.timezone_placeholder": "Select timezone",
        "recipe.schedule.daily": "Daily",
        "recipe.schedule.hourly": "Hourly",
        "recipe.schedule.weekly": "Weekly",
        "recipe.schedule.custom": "Custom",
        "recipe.schedule.daily_hint": "Runs every day at 9:00.",
        "recipe.schedule.hourly_hint": "Runs every hour at minute 0.",
        "recipe.schedule.weekly_hint": "Runs every Monday at 9:00.",
        email: "Email",
        copy: "Copy",
    },
}

const globalConfig = {
    global: {
        plugins: [
            createI18n({legacy: false, locale: "en", messages}),
            createPinia(),
        ],
        stubs: {
            KsText: {template: "<span><slot /></span>"},
            KsIcon: {template: "<span />"},
            KsTag: {template: "<span><slot /></span>"},
            KsCheckTag: {template: "<span @click=\"$emit('change')\"><slot /></span>", emits: ["change"]},
            KsAlert: {template: "<div><slot /></div>"},
            KsSelect: {template: "<select />"},
            KsOption: {template: "<option />"},
            KsCheckbox: {template: "<input type='checkbox' />"},
            KsFormItem: {template: "<div><slot /></div>"},
            KsInput: {template: "<input />"},
            KsCollapse: {template: "<div><slot /></div>"},
            KsCollapseItem: {template: "<div><slot /></div>"},
            KsButton: {template: "<button :disabled='disabled' @click=\"$emit('click')\"><slot /></button>", props: ["disabled"], emits: ["click"]},
            KsSkeleton: {template: "<div />"},
            KsEmpty: {template: "<div />"},
            KsSegmented: {template: "<div />"},
            KsEditor: {template: "<div />"},
            KsCard: {template: "<div><slot /></div>"},
            KsForm: {template: "<form><slot /></form>"},
            KsSteps: {template: "<div><slot /></div>"},
            KsStep: {template: "<div />"},
        },
    },
}

import FlowRecipe from "../../../../../src/components/flows/recipe/FlowRecipe.vue"

const next = async (wrapper: ReturnType<typeof mount>) => {
    await wrapper.find("[data-test='recipe-next-btn']").trigger("click")
    await wrapper.vm.$nextTick()
}

describe("FlowRecipe", () => {
    test("Next is disabled on the notify step until a channel is selected", async () => {
        // Given — a valid default trigger (execution + FAILED), advance to the notify step
        const wrapper = mount(FlowRecipe, globalConfig)
        await new Promise(r => setTimeout(r, 0))
        await next(wrapper)

        // Then — the notify step is shown and Next is blocked without a channel
        expect(wrapper.find("[data-test='recipe-step-notify']").exists()).toBe(true)
        const nextBtn = wrapper.find("[data-test='recipe-next-btn']")
        expect((nextBtn.element as HTMLButtonElement).disabled).toBe(true)
    })

    test("shows no-channel warning on the notify step when no channel is selected", async () => {
        // Given
        const wrapper = mount(FlowRecipe, globalConfig)
        await new Promise(r => setTimeout(r, 0))

        // When — advance to the notify step
        await next(wrapper)

        // Then — warning visible because no channel is selected
        const alert = wrapper.find("[data-test='recipe-no-channel-alert']")
        expect(alert.exists()).toBe(true)
    })

    test("trigger cards use unique keys (no duplicate key for case vs other)", () => {
        const wrapper = mount(FlowRecipe, globalConfig)
        const cards = wrapper.findAll("[data-test='recipe-trigger-types'] button[role='radio']")
        expect(cards.length).toBe(5)
    })

    test("renders a real icon for every trigger-type card and channel", async () => {
        // Regression: ISSUE-001 — KsIcon has no `name` prop, so `<KsIcon
        // :name="card.icon" />` silently rendered an empty icon for every
        // trigger-type card. Found by /qa on 2026-07-03.
        // Report: .gstack/qa-reports/qa-report-localhost-2026-07-03.md
        const wrapper = mount(FlowRecipe, {
            ...globalConfig,
            global: {
                ...globalConfig.global,
                stubs: {...globalConfig.global.stubs, KsIcon: {template: "<span><slot /></span>"}},
            },
        })
        await new Promise(r => setTimeout(r, 0))

        // Trigger step: one icon per trigger-type tile
        expect(wrapper.findAll(".trigger-card-icon svg").length).toBe(5)

        // Notify step: one icon per channel tile
        await next(wrapper)
        expect(wrapper.findAll(".icon-wrap svg").length).toBe(4)
    })

    test("emits submit with yaml once a channel is picked and create is clicked", async () => {
        // Given
        const wrapper = mount(FlowRecipe, {
            ...globalConfig,
            global: {
                ...globalConfig.global,
                provide: {},
            },
        })
        await new Promise(r => setTimeout(r, 0))

        // When — advance to notify, pick a channel, advance to review, click create
        await next(wrapper)
        const channelButtons = wrapper.findAll("[data-test='recipe-notify-grid'] button[role='checkbox']")
        await channelButtons[channelButtons.length - 1].trigger("click")
        await wrapper.vm.$nextTick()
        await next(wrapper)
        const createBtn = wrapper.find("[data-test='recipe-create-btn']")
        expect(createBtn.exists()).toBe(true)
        await createBtn.trigger("click")
        await wrapper.vm.$nextTick()

        // Then — submit fires with the generated yaml
        const submits = wrapper.emitted("submit")
        expect(submits).toBeTruthy()
        expect((submits![0][0] as {yaml: string}).yaml).toContain("io.kestra.plugin.core.trigger.Flow")
    })
})
