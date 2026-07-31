<template>
    <div class="flow-recipe" data-test="flow-recipe">
        <div class="recipe-wizard">
            <div class="wizard-rail">
                <KsSteps :space="72" direction="vertical" :active="activeStep" finishStatus="success">
                    <KsStep :title="$t('recipe.steps.trigger_title')" :description="$t('recipe.steps.trigger_desc')" />
                    <KsStep :title="$t('recipe.steps.notify_title')" :description="$t('recipe.steps.notify_desc')" />
                    <KsStep :title="$t('recipe.steps.review_title')" :description="$t('recipe.steps.review_desc')" />
                </KsSteps>
            </div>

            <KsCard class="wizard-body" shadow="never">
                <div v-if="activeStep === 0" class="wizard-step" data-test="recipe-step-trigger">
                    <KsText tag="h2" class="wizard-heading">{{ $t("recipe.steps.trigger_heading") }}</KsText>
                    <span class="wizard-sub">{{ $t("recipe.when.subtitle") }}</span>

                    <div class="trigger-types" role="radiogroup" :aria-label="$t('recipe.when.trigger_type')" data-test="recipe-trigger-types">
                        <SelectableTile
                            v-for="card in triggerCards"
                            :key="card.key"
                            role="radio"
                            indicator="radio"
                            :selected="recipe.triggerType === card.type && !card.disabled"
                            :disabled="card.disabled"
                            :ariaLabel="card.title"
                            @select="selectTrigger(card.type)"
                        >
                            <div class="trigger-card-icon">
                                <KsIcon>
                                    <component :is="card.icon" />
                                </KsIcon>
                            </div>
                            <div class="trigger-card-body">
                                <KsText class="trigger-card-title">{{ card.title }}</KsText>
                                <span class="trigger-card-sub">{{ card.sub }}</span>
                            </div>
                            <KsTooltip v-if="card.disabled" :content="$t('ee-tooltip.features-blocked')">
                                <KsTag size="small" class="ee-badge">EE</KsTag>
                            </KsTooltip>
                        </SelectableTile>
                    </div>

                    <KsAlert
                        v-if="namespacesError && recipe.triggerType === 'execution'"
                        type="error"
                        :closable="false"
                        class="wizard-alert"
                        data-test="recipe-namespaces-error"
                    >
                        {{ $t("recipe.execution.namespaces_error") }}
                    </KsAlert>

                    <div class="trigger-config">
                        <ExecutionPanel
                            v-if="recipe.triggerType === 'execution'"
                            :recipe="recipe"
                            :namespaceOptions="namespaceOptions"
                            :namespacesLoading="namespacesLoading"
                            :toggleState="toggleState"
                        />
                        <SchedulePanel
                            v-else-if="recipe.triggerType === 'schedule'"
                            :recipe="recipe"
                        />
                        <WebhookPanel
                            v-else-if="recipe.triggerType === 'webhook'"
                            :recipe="recipe"
                            :systemNamespace="systemNamespace"
                            :flowId="flowId"
                        />
                        <OtherPanel
                            v-else-if="recipe.triggerType === 'other'"
                            :recipe="recipe"
                            :setOtherTriggerType="setOtherTriggerType"
                        />
                    </div>
                </div>

                <div v-else-if="activeStep === 1" class="wizard-step" data-test="recipe-step-notify">
                    <KsText tag="h2" class="wizard-heading">{{ $t("recipe.steps.notify_heading") }}</KsText>
                    <span class="wizard-sub">{{ $t("recipe.then.subtitle") }}</span>

                    <NotifyGrid
                        :recipe="recipe"
                        :channelAvailability="channelAvailability"
                        :toggleNotify="toggleNotify"
                    />

                    <KsAlert
                        v-if="unavailableSelectedChannels.length > 0"
                        type="warning"
                        :closable="false"
                        class="wizard-alert"
                        data-test="recipe-dropped-channel-alert"
                    >
                        {{ $t("recipe.then.unavailable_channels_warning", {channels: unavailableChannelLabels}) }}
                    </KsAlert>

                    <KsAlert
                        v-if="hasInteracted && !hasNotifyChannel && unavailableSelectedChannels.length === 0"
                        type="warning"
                        :closable="false"
                        class="wizard-alert"
                        data-test="recipe-no-channel-alert"
                    >
                        {{ $t("recipe.then.no_channel_warning") }}
                    </KsAlert>
                </div>

                <div v-else class="wizard-step" data-test="recipe-step-review">
                    <KsText tag="h2" class="wizard-heading">{{ $t("recipe.steps.review_heading") }}</KsText>

                    <div class="review-hero">
                        <span class="hero-eyebrow">{{ $t("recipe.hero_eyebrow") }}</span>
                        <p class="hero-sentence" aria-live="polite" data-test="recipe-hero-sentence">
                            <template v-if="summary">{{ summary }}</template>
                            <span v-else class="hero-empty">{{ $t("recipe.hero_empty") }}</span>
                        </p>
                    </div>

                    <RecipeSummary
                        :yamlContent="yamlContent"
                        :isValid="isValid"
                        :hasChannel="hasNotifyChannel"
                        :triggerValid="isTriggerConfigValid"
                        :hasInteracted="hasInteracted"
                        @create="handleCreate"
                    />
                </div>

                <div class="wizard-nav">
                    <KsButton
                        v-if="activeStep > 0"
                        data-test="recipe-back-btn"
                        @click="prevStep"
                    >
                        {{ $t("back") }}
                    </KsButton>
                    <span class="wizard-nav-spacer" />
                    <KsButton
                        v-if="activeStep < 2"
                        type="primary"
                        :disabled="!canAdvance"
                        data-test="recipe-next-btn"
                        @click="nextStep"
                    >
                        {{ $t("next") }}
                    </KsButton>
                </div>
            </KsCard>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import {useMiscStore} from "override/stores/misc"
    import {useFlowRecipe} from "../../../composables/useFlowRecipe"
    import {recipeToYaml, SYSTEM_FLOW_RECIPE_ID} from "../../../utils/recipeToYaml"
    import {useNamespaceOptions} from "../../../composables/useNamespaceOptions"
    import {getRandomID} from "../../../utils/id"
    import type {NotifyChannel, TriggerType} from "../../../utils/recipeToYaml"

    import ExecutionPanel from "./triggerPanels/ExecutionPanel.vue"
    import SchedulePanel from "./triggerPanels/SchedulePanel.vue"
    import WebhookPanel from "./triggerPanels/WebhookPanel.vue"
    import OtherPanel from "./triggerPanels/OtherPanel.vue"
    import NotifyGrid from "./NotifyGrid.vue"
    import RecipeSummary from "./RecipeSummary.vue"
    import SelectableTile from "./SelectableTile.vue"

    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import ClockOutline from "vue-material-design-icons/ClockOutline.vue"
    import FolderMultipleOutline from "vue-material-design-icons/FolderMultipleOutline.vue"
    import Webhook from "vue-material-design-icons/Webhook.vue"
    import DotsHorizontal from "vue-material-design-icons/DotsHorizontal.vue"

    const props = withDefaults(defineProps<{
        namespace?: string
    }>(), {
        namespace: undefined,
    })

    const emit = defineEmits<{
        submit: [{id: string; namespace: string; yaml: string}]
    }>()

    const {t} = useI18n()
    const miscStore = useMiscStore()

    const systemNamespace = computed(() => props.namespace ?? miscStore.configs?.systemNamespace ?? "system")

    const flowId = `${SYSTEM_FLOW_RECIPE_ID}-${getRandomID()}`

    const {recipe, isValid, isTriggerConfigValid, hasNotifyChannel, unavailableSelectedChannels, summary, channelAvailability, availableFqcns, toggleNotify, toggleState, setOtherTriggerType} = useFlowRecipe()

    const {
        namespaces: namespaceOptions,
        loading: namespacesLoading,
        error: namespacesError,
    } = useNamespaceOptions()

    const hasInteracted = ref(false)
    const activeStep = ref(0)

    const canAdvance = computed(() => {
        if (activeStep.value === 0) return isTriggerConfigValid.value
        if (activeStep.value === 1) return hasNotifyChannel.value
        return true
    })

    const nextStep = () => {
        hasInteracted.value = true
        if (canAdvance.value && activeStep.value < 2) activeStep.value += 1
    }

    const prevStep = () => {
        if (activeStep.value > 0) activeStep.value -= 1
    }

    watch(recipe, () => {
        hasInteracted.value = true
    }, {deep: true})

    const yamlContent = computed(() => {
        try {
            return recipeToYaml(recipe, systemNamespace.value, availableFqcns.value, flowId)
        } catch {
            return ""
        }
    })

    const CHANNEL_LABELS: Record<NotifyChannel, () => string> = {
        slack: () => "Slack",
        teams: () => "Microsoft Teams",
        email: () => t("email"),
        custom: () => t("recipe.notify.custom_label"),
    }

    const unavailableChannelLabels = computed(() =>
        unavailableSelectedChannels.value.map(channel => CHANNEL_LABELS[channel]()).join(", "),
    )

    interface TriggerCard {
        key: string
        type: TriggerType | null
        icon: Component
        title: string
        sub: string
        disabled: boolean
    }

    const triggerCards = computed<TriggerCard[]>(() => [
        {
            key: "execution",
            type: "execution",
            icon: LightningBolt,
            title: t("recipe.trigger.execution_title"),
            sub: t("recipe.trigger.execution_sub"),
            disabled: false,
        },
        {
            key: "schedule",
            type: "schedule",
            icon: ClockOutline,
            title: t("recipe.trigger.schedule_title"),
            sub: t("recipe.trigger.schedule_sub"),
            disabled: false,
        },
        {
            key: "case",
            type: null,
            icon: FolderMultipleOutline,
            title: t("recipe.trigger.case_title"),
            sub: t("recipe.trigger.case_sub"),
            disabled: true,
        },
        {
            key: "webhook",
            type: "webhook",
            icon: Webhook,
            title: t("recipe.trigger.webhook_title"),
            sub: t("recipe.trigger.webhook_sub"),
            disabled: false,
        },
        {
            key: "other",
            type: "other",
            icon: DotsHorizontal,
            title: t("recipe.trigger.other_title"),
            sub: t("recipe.trigger.other_sub"),
            disabled: false,
        },
    ])

    const selectTrigger = (type: TriggerType | null) => {
        if (!type) return
        recipe.triggerType = type
        hasInteracted.value = true
    }

    const handleCreate = () => {
        if (!isValid.value) {
            hasInteracted.value = true
            return
        }
        emit("submit", {
            id: flowId,
            namespace: systemNamespace.value,
            yaml: yamlContent.value,
        })
    }
</script>

<style scoped lang="scss">
    .flow-recipe {
        padding: var(--ks-spacing-4);
    }

    .recipe-wizard {
        display: grid;
        grid-template-columns: 14rem minmax(0, 1fr);
        gap: var(--ks-spacing-5);
        align-items: start;

        @media (max-width: 900px) {
            grid-template-columns: 1fr;
        }
    }

    .wizard-rail {
        position: sticky;
        top: var(--ks-spacing-4);
        padding: var(--ks-spacing-4) 0;
    }

    .wizard-body {
        min-width: 0;
    }

    .wizard-step {
        display: flex;
        flex-direction: column;
    }

    .wizard-heading {
        margin: 0;
        align-self: flex-start;
        font-size: var(--ks-font-size-lg);
        font-weight: var(--ks-font-weight-semibold);
    }

    .wizard-sub {
        display: block;
        margin: var(--ks-spacing-1) 0 var(--ks-spacing-4);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .wizard-heading + .review-hero {
        margin-top: var(--ks-spacing-4);
    }

    .review-hero {
        padding: var(--ks-spacing-4) var(--ks-spacing-5);
        margin-bottom: var(--ks-spacing-4);
        background-color: var(--ks-bg-base);
        border-left: var(--ks-border-width-thick) solid var(--ks-border-focus);
        border-radius: var(--ks-radius-base);
    }

    .hero-eyebrow {
        display: block;
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-semibold);
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--ks-text-muted);
    }

    .hero-sentence {
        margin: var(--ks-spacing-2) 0 0;
        font-size: var(--ks-font-size-lg);
        font-weight: var(--ks-font-weight-medium);
        line-height: var(--ks-line-height-base);
        color: var(--ks-text-primary);
    }

    .hero-empty {
        color: var(--ks-text-muted);
        font-style: italic;
    }

    .trigger-config {
        margin-top: var(--ks-spacing-4);
        padding: var(--ks-spacing-4);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-base);
    }

    .wizard-alert {
        margin-top: var(--ks-spacing-4);
    }

    .wizard-nav {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-top: var(--ks-spacing-5);
        padding-top: var(--ks-spacing-4);
        border-top: var(--ks-border-width-thin) solid var(--ks-border-default);
    }

    .wizard-nav-spacer {
        flex: 1;
    }

    .trigger-types {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(min(12rem, 100%), 1fr));
        gap: var(--ks-spacing-3);
    }

    .trigger-card-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-6);
        height: var(--ks-spacing-6);
        border-radius: var(--ks-radius-sm);
        background-color: var(--ks-bg-tag);
        flex-shrink: 0;
        color: var(--ks-text-primary);
    }

    .trigger-card-body {
        flex: 1;
        min-width: 0;
    }

    .trigger-card-title {
        display: block;
        font-weight: var(--ks-font-weight-medium);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .trigger-card-sub {
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        overflow: hidden;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .ee-badge {
        flex-shrink: 0;
        margin-left: auto;
        font-size: var(--ks-font-size-xs);
    }
</style>
