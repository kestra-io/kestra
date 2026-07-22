import {computed, onMounted, reactive, ref} from "vue"
import {useI18n} from "vue-i18n"
import {stringUtils} from "@kestra-io/design-system"
import type {RecipeState, TriggerType} from "../utils/recipeToYaml"
import {NOTIFY_TASK_CONFIGS} from "../utils/recipeToYaml"
import {usePluginsStore} from "../stores/plugins"
import {extractPluginElements} from "../utils/pluginUtils"

export type {RecipeState, TriggerType}

const DEFAULT_STATE: RecipeState = {
    triggerType: "execution",
    watchNamespace: "",
    includeSub: true,
    states: ["FAILED", "WARNING"],
    cron: "0 9 * * *",
    timezone: "",
    webhookKey: "",
    otherTriggerType: "",
    notify: {
        slack: false,
        teams: false,
        email: false,
    },
    slackChannel: "#alerts",
    teamsWebhook: "",
    emailTo: "",
}

export function useFlowRecipe() {
    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    const recipe = reactive<RecipeState>({...DEFAULT_STATE, notify: {...DEFAULT_STATE.notify}})

    const availableFqcns = ref<Set<string>>(new Set())
    const fqcnsLoaded = ref(false)

    onMounted(async () => {
        try {
            const plugins = await pluginsStore.ensurePlugins()
            const fqcns = new Set<string>()
            for (const plugin of plugins) {
                const elements = extractPluginElements(plugin)
                for (const clsList of Object.values(elements)) {
                    for (const cls of clsList) {
                        fqcns.add(cls)
                    }
                }
            }
            availableFqcns.value = fqcns
        } finally {
            fqcnsLoaded.value = true
        }
    })

    const channelAvailability = computed(() => {
        if (!fqcnsLoaded.value || availableFqcns.value.size === 0) {
            return {slack: true, teams: true, email: true}
        }
        const isExecutionTrigger = recipe.triggerType === "execution"
        return {
            slack: availableFqcns.value.has(
                isExecutionTrigger
                    ? NOTIFY_TASK_CONFIGS.slack.executionFqcn
                    : NOTIFY_TASK_CONFIGS.slack.webhookFqcn,
            ),
            teams: availableFqcns.value.has(
                isExecutionTrigger
                    ? NOTIFY_TASK_CONFIGS.teams.executionFqcn
                    : NOTIFY_TASK_CONFIGS.teams.webhookFqcn,
            ),
            email: availableFqcns.value.has(
                isExecutionTrigger
                    ? NOTIFY_TASK_CONFIGS.email.executionFqcn
                    : NOTIFY_TASK_CONFIGS.email.webhookFqcn,
            ),
        }
    })

    const hasNotifyChannel = computed(() =>
        recipe.notify.slack || recipe.notify.teams || recipe.notify.email,
    )

    const isValid = computed(() => {
        if (!hasNotifyChannel.value) return false

        switch (recipe.triggerType) {
        case "execution":
            return recipe.states.length > 0
        case "schedule":
            return Boolean(recipe.cron)
        case "webhook":
            return Boolean(recipe.webhookKey)
        case "other":
            return Boolean(recipe.otherTriggerType)
        default:
            return false
        }
    })

    const summary = computed(() => {
        const channels: string[] = []
        if (recipe.notify.slack) channels.push("Slack")
        if (recipe.notify.teams) channels.push("Microsoft Teams")
        if (recipe.notify.email) channels.push(t("email"))

        const channelText = channels.length > 0
            ? channels.join(", ")
            : t("recipe.summary.no_channel")

        switch (recipe.triggerType) {
        case "execution": {
            const ns = recipe.watchNamespace || t("recipe.summary.any_namespace")
            const scope = recipe.includeSub
                ? t("recipe.summary.including_sub")
                : t("recipe.summary.exact_match")
            const stateText = recipe.states.length > 0
                ? recipe.states.join(", ")
                : "FAILED, WARNING"
            return t("recipe.summary.execution", {ns, scope, states: stateText, channels: channelText})
        }
        case "schedule":
            return t("recipe.summary.schedule", {cron: recipe.cron || "0 9 * * *", channels: channelText})
        case "webhook":
            return t("recipe.summary.webhook", {channels: channelText})
        case "other":
            return t("recipe.summary.other", {
                trigger: recipe.otherTriggerType
                    ? stringUtils.afterLastDot(recipe.otherTriggerType)
                    : t("recipe.summary.selected_trigger"),
                channels: channelText,
            })
        default:
            return ""
        }
    })

    function toggleNotify(key: keyof typeof recipe.notify) {
        recipe.notify[key] = !recipe.notify[key]
    }

    function toggleState(stateName: string) {
        const idx = recipe.states.indexOf(stateName)
        if (idx === -1) {
            recipe.states.push(stateName)
        } else {
            recipe.states.splice(idx, 1)
        }
    }

    function setOtherTriggerType(type: string) {
        recipe.otherTriggerType = recipe.otherTriggerType === type ? "" : type
    }

    function reset() {
        Object.assign(recipe, {...DEFAULT_STATE, notify: {...DEFAULT_STATE.notify}})
    }

    return {
        recipe,
        isValid,
        hasNotifyChannel,
        summary,
        channelAvailability,
        availableFqcns,
        toggleNotify,
        toggleState,
        setOtherTriggerType,
        reset,
    }
}
