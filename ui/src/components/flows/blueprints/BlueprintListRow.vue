<template>
    <div class="blueprint-row" @click="emit('click')">
        <div class="blueprint-row__main">
            <h3 class="blueprint-row__title">
                {{ blueprint.title ?? blueprint.id }}
            </h3>

            <div class="blueprint-row__meta">
                <div class="blueprint-row__icons">
                    <KsTaskIcon
                        v-for="task in visibleTasks"
                        :key="task"
                        :cls="task"
                        :icons="pluginsStore.icons"
                    />
                    <span v-if="hiddenTaskCount > 0" class="blueprint-row__overflow">
                        +{{ hiddenTaskCount }}
                    </span>
                </div>

                <div
                    v-if="blueprint.template || blueprint.tags?.length"
                    class="blueprint-row__tags"
                >
                    <span v-if="blueprint.template" class="tag-item">
                        {{ $t("template") }}
                    </span>
                    <span
                        v-for="tag in displayTags"
                        :key="tag.original"
                        class="tag-item"
                    >
                        {{ tag.display }}
                    </span>
                </div>
            </div>
        </div>

        <div class="blueprint-row__action">
            <KsTooltip
                trigger="click"
                :content="$t('copied')"
                placement="left"
                :autoClose="2000"
            >
                <KsButton
                    link
                    :icon="ContentCopy"
                    :aria-label="$t('copy')"
                    @click.prevent.stop="emit('copy')"
                />
            </KsTooltip>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import {usePluginsStore} from "../../../stores/plugins"
    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"

    const VISIBLE_TASK_LIMIT = 4

    const props = defineProps<{
        blueprint: FlowBlueprint;
        tags?: Record<string, BlueprintTag>;
    }>()

    const emit = defineEmits<{
        click: [];
        copy: [];
    }>()

    const pluginsStore = usePluginsStore()

    const uniqueTasks = computed(() => [...new Set(props.blueprint.includedTasks ?? [])])
    const visibleTasks = computed(() => uniqueTasks.value.slice(0, VISIBLE_TASK_LIMIT))
    const hiddenTaskCount = computed(() => Math.max(0, uniqueTasks.value.length - VISIBLE_TASK_LIMIT))

    const displayTags = computed(() =>
        (props.blueprint.tags ?? []).map(tag => ({
            original: tag,
            display: props.tags?.[tag]?.name ?? tag,
        })),
    )
</script>

<style lang="scss" scoped>
    .blueprint-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-4);
        padding: 1.25rem 2rem 1.25rem 1.25rem;
        background-color: var(--ks-bg-surface);
        border-bottom: 1px solid var(--ks-border-default);
        cursor: pointer;
        transition: background-color 0.15s ease;

        &:hover {
            background-color: var(--ks-bg-hover);

            .blueprint-row__action {
                opacity: 1;
                pointer-events: auto;
            }
        }

        &__main {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-3);
        }

        &__title {
            margin: 0;
            font-size: var(--ks-font-size-base);
            font-weight: 600;
            color: var(--ks-text-primary);
            line-height: 1.35;
            overflow-wrap: break-word;
        }

        &__meta {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            flex-wrap: wrap;
        }

        &__icons {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);

            :deep(.ks-task-icon) {
                width: 1.5rem;
                height: 1.5rem;
            }
        }

        &__overflow {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            height: 1.5rem;
            padding: 0 var(--ks-spacing-2);
            border-radius: 4.57px;
            border: 0.3px solid var(--ks-border-default);
            background-color: var(--ks-bg-tag);
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
            font-weight: 400;
            box-shadow: 0 0.3px 1.22px 0 var(--ks-shadow-element);
        }

        &__tags {
            display: flex;
            gap: var(--ks-spacing-1);
            flex-wrap: wrap;

            .tag-item {
                border: 1px solid var(--ks-border-default);
                border-radius: var(--ks-radius-base);
                padding: 0.25rem 0.5rem;
                background-color: var(--ks-bg-tag);
                color: var(--ks-text-primary);
                font-size: var(--ks-font-size-xs);
                font-weight: 400;
            }
        }

        &__action {
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.15s ease;
            flex-shrink: 0;
        }
    }
</style>
