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
                <div
                    v-for="task in uniqueTasks"
                    :key="task"
                    class="task"
                    :class="{missing: missingTasks.includes(task)}"
                >
                    <TaskIcon :cls="task" :icons="icons" :loadIcon="loadIcon" onlyIcon />
                    <span>{{ taskName(task) }}</span>
                </div>
            </div>
        </section>

        <section v-if="missingPlugins.length" class="block">
            <KsAlert
                type="warning"
                :closable="false"
                :title="$t('blueprints.missingPlugins.title')"
            >
                <p class="missing-description">
                    {{ $t("blueprints.missingPlugins.description", {plugins: missingPlugins.join(", ")}) }}
                </p>
                <slot name="missing-plugins-action" :missingPlugins="missingPlugins" />
            </KsAlert>
        </section>

        <section v-if="blueprint?.kind" class="block">
            <h4 class="label">{{ $t("blueprints.detail.links") }}</h4>
            <KsButton
                tag="a"
                :href="githubUrl"
                class="pill"
                target="_blank"
                rel="noopener noreferrer"
            >
                GitHub
                <OpenInNew />
            </KsButton>
        </section>
    </aside>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    import {stringUtils} from "@kestra-io/design-system"
    import TaskIcon from "../../plugins/TaskIcon.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import {useBlueprintPlugins} from "../../../composables/useBlueprintPlugins"
    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"

    const props = withDefaults(defineProps<{
        blueprint?: FlowBlueprint & {kind?: "FLOW" | "DASHBOARD" | "APP"};
        tags?: Record<string, BlueprintTag>;
        icons?: Record<string, any>;
        loadIcon?: (cls: string) => Promise<any>;
        columns?: number;
    }>(), {
        blueprint: undefined,
        tags: undefined,
        icons: () => ({}),
        loadIcon: undefined,
        columns: 1,
    })

    const GITHUB_REPO = "https://github.com/kestra-io/blueprints"

    const githubUrl = computed(() => {
        const kind = (props.blueprint?.kind ?? "flow").toLowerCase()
        const directory = `${kind}s`
        return props.blueprint?.id
            ? `${GITHUB_REPO}/blob/main/${directory}/${props.blueprint.id}.yaml`
            : `${GITHUB_REPO}/tree/main/${directory}`
    })

    const processedTags = computed(() =>
        (props.blueprint?.tags ?? []).map(id => ({
            id,
            label: props.tags?.[id]?.name ?? id,
        })),
    )

    const uniqueTasks = computed(() => [...new Set(props.blueprint?.includedTasks)])

    const taskName = (cls: string) => stringUtils.afterLastDot(cls)

    const {missingTaskTypes, missingPluginNames} = useBlueprintPlugins()

    const missingTasks = computed(() =>
        missingTaskTypes(props.blueprint?.includedTasks),
    )

    const missingPlugins = computed(() =>
        missingPluginNames(props.blueprint?.includedTasks),
    )
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

            :deep(.task-icon) {
                flex-shrink: 0;
                width: 1.5rem;
                height: 1.5rem;
            }

            &.missing {
                color: var(--ks-text-secondary);

                :deep(.task-icon) {
                    opacity: 0.4;
                    filter: grayscale(1);
                }
            }
        }

        .missing-description {
            margin: 0;
        }

        .pill {
            width: fit-content;
            padding: 0 var(--ks-spacing-3);
            border: none;
            border-radius: var(--ks-radius-sm);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-bold);

            :deep(.open-in-new-icon) {
                margin-left: 0.25rem;
            }
        }
    }
</style>
