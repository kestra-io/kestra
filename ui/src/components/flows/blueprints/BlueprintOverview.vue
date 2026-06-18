<template>
    <aside class="overview">
        <h3 class="heading">{{ $t("overview") }}</h3>

        <section v-if="processedTags.length" class="block">
            <h4 class="label">{{ $t("blueprints.detail.category") }}</h4>
            <div class="tags">
                <KsTag v-for="tag in processedTags" :key="tag.id" class="pill">
                    {{ tag.label }}
                </KsTag>
            </div>
        </section>

        <section v-if="uniqueTasks.length" class="block">
            <h4 class="label">{{ $t("tasks") }}</h4>
            <div class="tasks" :style="{'--task-columns': columns}">
                <div v-for="task in uniqueTasks" :key="task" class="task">
                    <KsTaskIcon :cls="task" :icons="icons" onlyIcon />
                    <span>{{ taskName(task) }}</span>
                </div>
            </div>
        </section>

        <section class="block">
            <h4 class="label">{{ $t("blueprints.detail.links") }}</h4>
            <KsLink :href="GITHUB_URL" class="pill" target="_blank" underline="never">
                GitHub
                <OpenInNew :size="14" />
            </KsLink>
        </section>
    </aside>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    import {KsTaskIcon, stringUtils} from "@kestra-io/design-system"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"

    const GITHUB_URL = "https://github.com/kestra-io/blueprints/tree/main/flows"

    const props = withDefaults(defineProps<{
        blueprint?: FlowBlueprint;
        tags?: Record<string, BlueprintTag>;
        icons?: Record<string, any>;
        columns?: number;
    }>(), {
        blueprint: undefined,
        tags: undefined,
        icons: () => ({}),
        columns: 1,
    })

    const processedTags = computed(() =>
        (props.blueprint?.tags ?? []).map(id => ({
            id,
            label: props.tags?.[id]?.name ?? id,
        })),
    )

    const uniqueTasks = computed(() => [...new Set(props.blueprint?.includedTasks)])

    const taskName = (cls: string) => stringUtils.afterLastDot(cls)
</script>

<style scoped lang="scss">
    .overview {
        display: flex;
        flex-direction: column;
        gap: 1.3125rem;
        padding: var(--ks-spacing-6) var(--ks-spacing-6) var(--ks-spacing-4) 1.125rem;

        .heading {
            margin: 0;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-md);
            font-weight: var(--ks-font-weight-bold);
            text-transform: uppercase;
        }

        .block {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-3);
        }

        .block + .block {
            padding-top: var(--ks-spacing-5);
            border-top: 1px solid var(--ks-border-default);
        }

        .label {
            margin: 0;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-sm);
            font-weight: var(--ks-font-weight-bold);
        }

        .tags {
            display: flex;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2);
        }

        .tasks {
            display: grid;
            grid-template-columns: repeat(var(--task-columns, 1), minmax(0, 1fr));
            gap: var(--ks-spacing-3);
        }

        .task {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-regular);

            :deep(.ks-task-icon) {
                flex-shrink: 0;
                width: 1.5rem;
                height: 1.5rem;
            }
        }

        .pill {
            width: fit-content;
            padding: 0.125rem 0.375rem;
            border: none;
            border-radius: var(--ks-radius-sm);
            background: var(--ks-bg-tag);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-regular);

            :deep(.open-in-new-icon) {
                margin-left: 0.25rem;
            }
        }
    }
</style>
