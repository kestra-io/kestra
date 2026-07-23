<template>
    <div class="flow-recipe" data-test="flow-recipe">
        <div class="recipe-hero">
            <span class="hero-eyebrow">{{ $t("recipe.hero_eyebrow") }}</span>
            <p class="hero-sentence" aria-live="polite" data-test="recipe-hero-sentence">
                <template v-if="summary">{{ summary }}</template>
                <span v-else class="hero-empty">{{ $t("recipe.hero_empty") }}</span>
            </p>
        </div>

        <div class="recipe-layout">
            <div class="recipe-builder">
                <KsCard class="recipe-block" shadow="never">
                    <div class="block-head">
                        <span class="block-num">1</span>
                        <KsText tag="h2" class="block-title">{{ $t("recipe.when.title") }}</KsText>
                    </div>
                    <span class="block-sub">{{ $t("recipe.when.subtitle") }}</span>

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
                            <KsTag v-if="card.disabled" size="small" class="ee-badge">EE</KsTag>
                        </SelectableTile>
                    </div>

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
                </KsCard>

                <KsCard class="recipe-block" shadow="never">
                    <div class="block-head">
                        <span class="block-num">2</span>
                        <KsText tag="h2" class="block-title">{{ $t("recipe.then.title") }}</KsText>
                    </div>
                    <span class="block-sub">{{ $t("recipe.then.subtitle") }}</span>

                    <NotifyGrid
                        :recipe="recipe"
                        :channelAvailability="channelAvailability"
                        :toggleNotify="toggleNotify"
                    />

                    <KsAlert
                        v-if="hasInteracted && !hasNotifyChannel"
                        type="warning"
                        :closable="false"
                        class="block-alert"
                        data-test="recipe-no-channel-alert"
                    >
                        {{ $t("recipe.then.no_channel_warning") }}
                    </KsAlert>
                </KsCard>
            </div>

            <div class="recipe-summary">
                <RecipeSummary
                    :yamlContent="yamlContent"
                    :isValid="isValid"
                    :hasChannel="hasNotifyChannel"
                    :triggerValid="isTriggerConfigValid"
                    :hasInteracted="hasInteracted"
                    @create="handleCreate"
                />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useMiscStore} from "override/stores/misc"
    import {useFlowRecipe} from "../../../composables/useFlowRecipe"
    import {recipeToYaml, SYSTEM_FLOW_RECIPE_ID} from "../../../utils/recipeToYaml"
    import useNamespaces from "../../../composables/useNamespaces"
    import type {TriggerType} from "../../../utils/recipeToYaml"

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

    const flowId = `${SYSTEM_FLOW_RECIPE_ID}-${Math.random().toString(36).slice(2, 7)}`

    const {recipe, isValid, isTriggerConfigValid, hasNotifyChannel, summary, channelAvailability, availableFqcns, toggleNotify, toggleState, setOtherTriggerType} = useFlowRecipe()

    const namespaceOptions = ref<string[]>([])
    const namespacesLoading = ref(false)
    const hasInteracted = ref(false)

    onMounted(async () => {
        namespacesLoading.value = true
        try {
            const ns = await useNamespaces(500).all()
            namespaceOptions.value = ns.map(n => n.id)
        } catch {
            namespaceOptions.value = []
        } finally {
            namespacesLoading.value = false
        }
    })

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

    const triggerCards = computed(() => [
        {
            key: "execution",
            type: "execution" as TriggerType,
            icon: LightningBolt,
            title: t("recipe.trigger.execution_title"),
            sub: t("recipe.trigger.execution_sub"),
            disabled: false,
        },
        {
            key: "schedule",
            type: "schedule" as TriggerType,
            icon: ClockOutline,
            title: t("recipe.trigger.schedule_title"),
            sub: t("recipe.trigger.schedule_sub"),
            disabled: false,
        },
        {
            key: "case",
            type: "other" as TriggerType,
            icon: FolderMultipleOutline,
            title: t("recipe.trigger.case_title"),
            sub: t("recipe.trigger.case_sub"),
            disabled: true,
        },
        {
            key: "webhook",
            type: "webhook" as TriggerType,
            icon: Webhook,
            title: t("recipe.trigger.webhook_title"),
            sub: t("recipe.trigger.webhook_sub"),
            disabled: false,
        },
        {
            key: "other",
            type: "other" as TriggerType,
            icon: DotsHorizontal,
            title: t("recipe.trigger.other_title"),
            sub: t("recipe.trigger.other_sub"),
            disabled: false,
        },
    ])

    const selectTrigger = (type: TriggerType) => {
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

    .recipe-hero {
        padding: var(--ks-spacing-5) var(--ks-spacing-6);
        margin-bottom: var(--ks-spacing-4);
        background-color: var(--ks-bg-surface);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-left: var(--ks-border-width-thick, 4px) solid var(--ks-border-focus);
        border-radius: var(--ks-radius-lg);
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
        font-size: var(--ks-font-size-xl);
        font-weight: var(--ks-font-weight-medium);
        line-height: 1.6;
        color: var(--ks-text-primary);
    }

    .hero-empty {
        color: var(--ks-text-muted);
        font-style: italic;
    }

    .recipe-layout {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 22rem;
        gap: var(--ks-spacing-5);
        align-items: start;

        @media (max-width: 900px) {
            grid-template-columns: 1fr;
        }
    }

    .recipe-builder {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .block-head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-1);
    }

    .block-num {
        flex: none;
        width: var(--ks-spacing-5);
        height: var(--ks-spacing-5);
        border-radius: var(--ks-radius-round, 999px);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);
        background-color: var(--ks-bg-tag-active);
        color: var(--ks-text-primary);
    }

    .block-title {
        margin: 0;
        align-self: center;
        font-size: var(--ks-font-size-lg);
        font-weight: var(--ks-font-weight-semibold);
    }

    .block-sub {
        display: block;
        margin: 0 0 var(--ks-spacing-4) calc(var(--ks-spacing-5) + var(--ks-spacing-3));
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .trigger-config {
        margin-top: var(--ks-spacing-4);
        padding: var(--ks-spacing-4);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-base);
    }

    .block-alert {
        margin-top: var(--ks-spacing-4);
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

    .recipe-summary {
        min-width: 0;
    }
</style>
