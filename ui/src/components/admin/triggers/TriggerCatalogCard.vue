<template>
    <KsTooltip
        :disabled="!trigger.description"
        placement="top"
        :showAfter="250"
        :hideAfter="0"
        effect="light"
        :popperStyle="TOOLTIP_POPPER_STYLE"
    >
        <template #content>
            <KsMarkdown v-if="trigger.description" :content="trigger.description" />
        </template>
        <div class="trigger-card" @click="$emit('add', trigger)">
            <div class="card-header">
                <KsTaskIcon class="trigger-icon" :cls="trigger.type" :icons="pluginsStore.icons" onlyIcon />
                <span class="trigger-name">{{ displayName }}</span>
                <KsTag
                    v-if="trigger.ee"
                    type="info"
                    size="small"
                    :title="$t('triggers_add_ee_tooltip')"
                >
                    EE
                </KsTag>
                <KsButton type="primary" class="add-button">
                    {{ $t("triggers_add_card_add") }}
                </KsButton>
            </div>
            <div class="trigger-description">
                <template v-for="(part, i) in descriptionParts" :key="i">
                    <code v-if="i % 2 === 1">{{ part.slice(1, -1) }}</code>
                    <template v-else>
                        {{ part }}
                    </template>
                </template>
            </div>
        </div>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsMarkdown, KsTaskIcon} from "@kestra-io/design-system"
    import {usePluginsStore, type TriggerPluginDto} from "../../../stores/plugins"
    import {triggerDisplayName} from "./triggerCatalog"

    const props = defineProps<{ trigger: TriggerPluginDto }>()
    defineEmits<{ add: [trigger: TriggerPluginDto] }>()

    const TOOLTIP_POPPER_STYLE = {
        maxWidth: "26.25rem",
        fontSize: "0.75rem",
        lineHeight: "1.5",
        padding: "0.625rem 0.75rem",
        color: "var(--ks-content-primary)",
    }

    const pluginsStore = usePluginsStore()
    const displayName = computed(() => triggerDisplayName(props.trigger))
    const descriptionParts = computed(() => (props.trigger.description ?? "").split(/(`[^`]+`)/g))
</script>

<style scoped lang="scss">
    .trigger-card {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        padding: 1rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: 0.5rem;
        background: var(--ks-background-card);
        transition: border-color 0.12s ease;
        cursor: pointer;

        &:hover {
            border-color: var(--ks-border-active);
        }
    }

    .card-header {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-width: 0;
    }

    .trigger-icon {
        width: 2rem;
        height: 2rem;
        flex-shrink: 0;
    }

    .add-button {
        margin-left: auto;
    }

    .trigger-name {
        flex: 1;
        min-width: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--ks-content-primary);
    }

    .trigger-description {
        font-size: 0.75rem;
        line-height: 1.4;
        color: var(--ks-content-tertiary);
        min-height: calc(1.4em * 2);
        display: -webkit-box;
        line-clamp: 2;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;

        code {
            font-family: var(--ks-font-family-mono);
            font-size: 0.92em;
            padding: 1px 0.25rem;
            border-radius: 3px;
            background: var(--ks-tag-background);
            color: var(--ks-content-primary);
        }
    }
</style>
