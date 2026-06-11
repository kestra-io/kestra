<template>
    <div class="trigger-card">
        <div class="card-body">
            <div class="trigger-icon-box">
                <KsTaskIcon class="trigger-icon" :cls="trigger.type" :icons="pluginsStore.icons" onlyIcon />
            </div>
            <div class="card-content">
                <div class="card-title-row">
                    <span class="trigger-name">{{ displayName }}</span>
                    <KsTooltip
                        v-if="trigger.description"
                        placement="bottom-end"
                        :showAfter="250"
                        :hideAfter="0"
                        effect="light"
                        :popperStyle="TOOLTIP_POPPER_STYLE"
                    >
                        <template #content>
                            <KsMarkdown :content="trigger.description" />
                        </template>
                        <KsIcon class="info-icon" :size="16">
                            <InformationOutline />
                        </KsIcon>
                    </KsTooltip>
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
        </div>

        <div class="card-divider" />

        <div class="card-footer">
            <div class="card-tags">
                <span class="category-tag">{{ $t(`triggers_add_filter_${trigger.group}`) }}</span>
                <KsTag
                    v-if="trigger.ee"
                    type="info"
                    size="small"
                    :title="$t('triggers_add_ee_tooltip')"
                >
                    EE
                </KsTag>
            </div>
            <KsButton class="add-button" @click="$emit('add', trigger)">
                <template #icon>
                    <Plus />
                </template>
                {{ $t("triggers_add_card_add") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsMarkdown, KsTaskIcon} from "@kestra-io/design-system"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import {usePluginsStore, type TriggerPluginDto} from "../../../stores/plugins"
    import {triggerDisplayName} from "./triggerCatalog"

    const props = defineProps<{ trigger: TriggerPluginDto }>()
    defineEmits<{ add: [trigger: TriggerPluginDto] }>()

    const TOOLTIP_POPPER_STYLE = {
        maxWidth: "26.25rem",
        fontSize: "var(--ks-font-size-xs)",
        lineHeight: "var(--ks-line-height-base)",
        padding: "0.625rem var(--ks-spacing-3)",
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
        min-height: 8.75rem;
        padding: var(--ks-spacing-4) var(--ks-spacing-4) var(--ks-spacing-2);
        border: var(--ks-border-block-primary);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
        box-shadow: 0 2px 8px 0 var(--ks-shadow-surface);
        transition: border-color var(--ks-duration-base) var(--ks-ease-standard);

        &:hover {
            border-color: var(--ks-border-strong);
        }
    }

    .card-body {
        display: flex;
        gap: var(--ks-spacing-3);
        flex: 1;
        min-height: 0;
    }

    .trigger-icon-box {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 2.625rem;
        height: 2.625rem;
        flex-shrink: 0;
        border-radius: var(--ks-radius-base);
        border: 0.53px solid var(--ks-border-default);
        background: var(--ks-white);
        box-shadow: 0 0.53px 2.13px 0 var(--ks-shadow-element);
    }

    .trigger-icon {
        width: var(--ks-icon-size-xl);
        height: var(--ks-icon-size-xl);
    }

    .card-content {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        flex: 1;
        min-width: 0;
    }

    .card-title-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
    }

    .trigger-name {
        flex: 1;
        min-width: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: var(--ks-font-size-md);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .info-icon {
        flex-shrink: 0;
        color: var(--ks-icon-muted);

        &:hover {
            color: var(--ks-text-primary);
            cursor: pointer;
        }
    }

    .trigger-description {
        font-size: var(--ks-font-size-xs);
        line-height: 20px;
        color: var(--ks-text-secondary);
        min-height: calc(1.4em * 2);
        display: -webkit-box;
        line-clamp: 2;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;

        code {
            font-family: var(--ks-font-family-mono);
            font-size: 0.92em;
            padding: var(--ks-spacing-px) var(--ks-spacing-1);
            border-radius: var(--ks-radius-xs);
            background: var(--ks-tag-background);
            color: var(--ks-text-primary);
        }
    }

    .card-divider {
        height: var(--ks-border-width-thin);
        background: var(--ks-border-subtle);
        margin: var(--ks-spacing-3) 0;
    }

    .card-footer {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .card-tags {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
        min-width: 0;
    }

    .category-tag {
        padding: 0.125rem 0.375rem;
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-tag);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-primary);
    }

    .add-button {
        margin-left: auto;
        flex-shrink: 0;
        padding: 0 var(--ks-spacing-3);
    }
</style>
