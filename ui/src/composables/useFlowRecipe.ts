import {computed, onMounted, reactive, ref} from "vue"
import {useI18n} from "vue-i18n"
import {stringUtils} from "@kestra-io/design-system"
import type {NotifyChannel, RecipeState, TriggerType} from "../utils/recipeToYaml"
import {
    DEFAULT_CRON,
    DEFAULT_SLACK_CHANNEL,
    DEFAULT_STATES,
    notifyTaskFqcn,
} from "../utils/recipeToYaml"
import {usePluginsStore} from "../stores/plugins"
import {extractPluginElements} from "../utils/pluginUtils"

export type {NotifyChannel, RecipeState, TriggerType}

const PLUGIN_BACKED_CHANNELS = ["slack", "teams", "email"] as const

function createDefaultState(): RecipeState {
    return {
        triggerType: "execution",
        watchNamespace: "",
        includeSub: true,
        states: [...DEFAULT_STATES],
        cron: DEFAULT_CRON,
        timezone: "",
        webhookKey: "",
        otherTriggerType: "",
        notify: {
            slack: false,
            teams: false,
            email: false,
            custom: false,
        },
        slackChannel: DEFAULT_SLACK_CHANNEL,
        teamsWebhook: "",
        emailTo: "",
    }
}

export function useFlowRecipe() {
    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    const recipe = reactive<RecipeState>(createDefaultState())

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
        } catch {
            availableFqcns.value = new Set()
        } finally {
            fqcnsLoaded.value = true
        }
    })

    const channelAvailability = computed<Record<NotifyChannel, boolean>>(() => {
        if (!fqcnsLoaded.value || availableFqcns.value.size === 0) {
            return {slack: true, teams: true, email: true, custom: true}
        }

        const isExecutionTrigger = recipe.triggerType === "execution"
        const availability = {custom: true} as Record<NotifyChannel, boolean>
        for (const channel of PLUGIN_BACKED_CHANNELS) {
            availability[channel] = availableFqcns.value.has(notifyTaskFqcn(channel, isExecutionTrigger))
        }
        return availability
    })

    const selectedChannels = computed<NotifyChannel[]>(() =>
        (Object.keys(recipe.notify) as NotifyChannel[]).filter(channel => recipe.notify[channel]),
    )

    const unavailableSelectedChannels = computed(() =>
        selectedChannels.value.filter(channel => !channelAvailability.value[channel]),
    )

    const hasNotifyChannel = computed(() =>
        selectedChannels.value.some(channel => channelAvailability.value[channel]),
    )

    const isTriggerConfigValid = computed(() => {
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

    const isValid = computed(() => hasNotifyChannel.value && isTriggerConfigValid.value)

    const summary = computed(() => {
        const labels: Record<NotifyChannel, string> = {
            slack: "Slack",
            teams: "Microsoft Teams",
            email: t("email"),
            custom: t("recipe.notify.custom_label"),
        }
        const channels = selectedChannels.value
            .filter(channel => channelAvailability.value[channel])
            .map(channel => labels[channel])

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
                : DEFAULT_STATES.join(", ")
            return t("recipe.summary.execution", {ns, scope, states: stateText, channels: channelText})
        }
        case "schedule":
            return t("recipe.summary.schedule", {cron: recipe.cron || DEFAULT_CRON, channels: channelText})
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

    function toggleNotify(key: NotifyChannel) {
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
        Object.assign(recipe, createDefaultState())
    }

    return {
        recipe,
        isValid,
        isTriggerConfigValid,
        hasNotifyChannel,
        unavailableSelectedChannels,
        summary,
        channelAvailability,
        availableFqcns,
        toggleNotify,
        toggleState,
        setOtherTriggerType,
        reset,
    }
}
