<template>
    <div class="flow-recipe" data-test="flow-recipe">
        <div class="recipe-layout">
            <div class="recipe-builder">
                <div class="section">
                    <KsText tag="h2" class="section-title">{{ $t("recipe.when.title") }}</KsText>
                    <span class="section-sub">{{ $t("recipe.when.subtitle") }}</span>
                </div>

                <div class="trigger-types" role="radiogroup" :aria-label="$t('recipe.when.trigger_type')" data-test="recipe-trigger-types">
                    <div
                        v-for="card in triggerCards"
                        :key="card.key"
                        class="trigger-card"
                        :class="{
                            selected: recipe.triggerType === card.type && !card.disabled,
                            disabled: card.disabled,
                        }"
                        role="radio"
                        :aria-checked="recipe.triggerType === card.type && !card.disabled"
                        :aria-disabled="card.disabled"
                        :tabindex="card.disabled ? -1 : 0"
                        @click="!card.disabled && selectTrigger(card.type)"
                        @keydown.enter="!card.disabled && selectTrigger(card.type)"
                        @keydown.space.prevent="!card.disabled && selectTrigger(card.type)"
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
                        <div class="trigger-card-right">
                            <KsTag v-if="card.disabled" size="small" class="ee-badge">EE</KsTag>
                            <KsIcon v-else-if="recipe.triggerType === card.type" class="check-icon">
                                <CheckCircle />
                            </KsIcon>
                        </div>
                    </div>
                </div>

                <div class="trigger-panel">
                    <ExecutionPanel
                        v-if="recipe.triggerType === 'execution'"
                        :recipe="recipe"
                        :namespaceOptions="namespaceOptions"
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
                    />
                    <OtherPanel
                        v-else-if="recipe.triggerType === 'other'"
                        :recipe="recipe"
                        :setOtherTriggerType="setOtherTriggerType"
                    />
                </div>

                <div class="section">
                    <KsText tag="h2" class="section-title">{{ $t("recipe.then.title") }}</KsText>
                    <span class="section-sub">{{ $t("recipe.then.subtitle") }}</span>
                </div>

                <NotifyGrid
                    :recipe="recipe"
                    :channelAvailability="channelAvailability"
                    :toggleNotify="toggleNotify"
                />

                <KsAlert
                    v-if="hasInteracted && !hasNotifyChannel"
                    type="warning"
                    :closable="false"
                    data-test="recipe-no-channel-alert"
                >
                    {{ $t("recipe.then.no_channel_warning") }}
                </KsAlert>
            </div>

            <div class="recipe-summary">
                <RecipeSummary
                    :summary="summary"
                    :yamlContent="yamlContent"
                    :isValid="isValid"
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
    import {recipeToYaml} from "../../../utils/recipeToYaml"
    import useNamespaces from "../../../composables/useNamespaces"
    import type {TriggerType} from "../../../utils/recipeToYaml"

    import ExecutionPanel from "./triggerPanels/ExecutionPanel.vue"
    import SchedulePanel from "./triggerPanels/SchedulePanel.vue"
    import WebhookPanel from "./triggerPanels/WebhookPanel.vue"
    import OtherPanel from "./triggerPanels/OtherPanel.vue"
    import NotifyGrid from "./NotifyGrid.vue"
    import RecipeSummary from "./RecipeSummary.vue"

    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import ClockOutline from "vue-material-design-icons/ClockOutline.vue"
    import FolderMultipleOutline from "vue-material-design-icons/FolderMultipleOutline.vue"
    import Webhook from "vue-material-design-icons/Webhook.vue"
    import DotsHorizontal from "vue-material-design-icons/DotsHorizontal.vue"
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue"

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

    const {recipe, isValid, hasNotifyChannel, summary, channelAvailability, availableFqcns, toggleNotify, toggleState, setOtherTriggerType} = useFlowRecipe()

    const namespaceOptions = ref<string[]>([])
    const hasInteracted = ref(false)

    onMounted(async () => {
        try {
            const ns = await useNamespaces(500).all()
            namespaceOptions.value = ns.map(n => n.id)
        } catch {
            namespaceOptions.value = []
        }
    })

    watch(recipe, () => {
        hasInteracted.value = true
    }, {deep: true})

    const yamlContent = computed(() => {
        try {
            return recipeToYaml(recipe, systemNamespace.value, availableFqcns.value)
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
            id: "system-flow-alert",
            namespace: systemNamespace.value,
            yaml: yamlContent.value,
        })
    }
</script>

<style scoped lang="scss">
    .flow-recipe {
        padding: var(--ks-spacing-4);
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

    .section {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .section-title {
        margin: 0;
        align-self: flex-start;
        font-weight: var(--ks-font-weight-semibold);
    }

    .section-sub {
        display: block;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .trigger-types {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
        gap: var(--ks-spacing-3);
    }

    .trigger-card {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        cursor: pointer;
        transition: border-color var(--ks-duration-fast) var(--ks-ease-standard), background-color var(--ks-duration-fast) var(--ks-ease-standard);

        &:hover:not(.disabled) {
            border-color: var(--ks-border-strong);
            background-color: var(--ks-bg-hover);
        }

        &.selected {
            border-color: var(--ks-border-focus);
            background-color: var(--ks-bg-tag-active);
        }

        &.disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 2px;
        }
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

    .trigger-card-right {
        flex-shrink: 0;
        margin-left: auto;
    }

    .ee-badge {
        font-size: var(--ks-font-size-xs);
    }

    .check-icon {
        color: var(--ks-text-link);
    }

    .trigger-panel {
        padding: var(--ks-spacing-4);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-surface);
    }

    .recipe-summary {
        min-width: 0;
    }
</style>
