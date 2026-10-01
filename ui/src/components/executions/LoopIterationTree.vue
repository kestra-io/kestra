<template>
    <div class="loop-iteration-tree">
        <div
            class="loop-toggle"
            :style="{'--depth': depth}"
            data-test="loop-iteration-toggle"
            @click="onToggle"
        >
            <ChevronDown v-if="expanded" />
            <ChevronRight v-else />
            <span class="loop-toggle__label">{{ $t("iterations") }}</span>
            <span v-if="loaded" class="loop-toggle__count">{{ total }}</span>
        </div>

        <template v-if="expanded">
            <div v-if="loading && iterations.length === 0" class="loop-loading">
                <KsIcon class="is-loading" :size="20">
                    <Loading />
                </KsIcon>
            </div>

            <KsAlert v-else-if="error" type="error">
                {{ $t("loop_iterations_load_error") }}
                <KsButton size="small" link @click="ensureLoaded">{{ $t("retry") }}</KsButton>
            </KsAlert>

            <div v-else-if="loaded && iterations.length === 0" class="loop-empty">
                {{ $t("no_iterations") }}
            </div>

            <div
                v-for="iteration in iterations"
                :key="iteration.id"
                class="loop-iteration-row"
                data-test="loop-iteration-row"
            >
                <div 
                    class="loop-iteration-row__main"
                    :style="{'--depth': depth + 1}"
                    @click="toggleRow(iteration.id)"
                >
                    <ChevronDown v-if="expandedRows.has(iteration.id)" class="loop-iteration-row__chevron" />
                    <ChevronRight v-else class="loop-iteration-row__chevron" />
                    <KsExecutionStatus size="small" :status="iteration.state.current" />
                    <span class="loop-iteration-row__value">{{ iteration.value }}</span>
                    <Duration class="loop-iteration-row__duration" :histories="iteration.state.histories" />
                    <SubFlowLink
                        :executionId="iteration.id"
                        :namespace="namespace"
                        :flowId="flowId"
                        tabExecution="gantt"
                        @click.stop
                    />
                </div>

                <IterationTaskRuns
                    v-if="expandedRows.has(iteration.id)"
                    :executionId="iteration.id"
                    :namespace="namespace"
                    :flowId="flowId"
                    :depth="depth + 1"
                />
            </div>

            <div v-if="needsPreview" class="loop-preview-footer">
                <span class="loop-preview-footer__count">
                    {{ $t("loop_iterations_shown", {shown: iterations.length, total}) }}
                </span>
                <KsButton
                    v-if="hasMore"
                    size="small"
                    link
                    :loading="loading"
                    data-test="loop-load-more"
                    @click="loadMore"
                >
                    {{ $t("load_10_more") }}
                </KsButton>
                <KsButton
                    :tag="RouterLink"
                    size="small"
                    link
                    :to="allExecutionsRoute"
                >
                    {{ $t("show_all_executions") }}
                </KsButton>
            </div>

            <label class="loop-failed-filter">
                <KsCheckbox
                    :modelValue="failedOnly"
                    @update:modelValue="onFailedOnlyChange"
                />
                {{ $t("failed_iterations_only") }}
            </label>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, reactive} from "vue"
    import {RouterLink} from "vue-router"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import {Duration} from "@kestra-io/topology"
    import {KsExecutionStatus, KsButton, KsCheckbox, KsIcon} from "@kestra-io/design-system"
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import IterationTaskRuns from "./IterationTaskRuns.vue"
    import {useLoopIterations} from "../../composables/useLoopIterations"

    const props = defineProps<{
        executionId: string;
        taskId: string;
        namespace: string;
        flowId: string;
        depth?: number;
    }>()

    const depth = props.depth ?? 0

    const {
        iterations,
        total,
        loading,
        error,
        loaded,
        hasMore,
        needsPreview,
        failedOnly,
        ensureLoaded,
        loadMore,
        setFailedOnly,
    } = useLoopIterations(props.executionId, props.taskId)

    const expanded = ref(false)
    // Which iteration rows have their task runs expanded — per-row, not all-or-nothing,
    // so opening one iteration's detail doesn't fetch or render the other nine on this page.
    const expandedRows = reactive(new Set<string>())

    function onToggle() {
        expanded.value = !expanded.value
        if (expanded.value) ensureLoaded()
    }

    function toggleRow(iterationId: string) {
        if (expandedRows.has(iterationId)) {
            expandedRows.delete(iterationId)
        } else {
            expandedRows.add(iterationId)
        }
    }

    function onFailedOnlyChange(value: boolean) {
        setFailedOnly(value)
    }

    const allExecutionsRoute = {
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": props.executionId,
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": props.taskId,
        },
    }
</script>

<style scoped lang="scss">
    .loop-toggle {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-4);
        padding-left: calc(var(--ks-spacing-4) + var(--depth, 0) * var(--ks-spacing-5));
        cursor: pointer;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);

        &__count {
            color: var(--ks-text-muted);
        }
    }

    .loop-loading {
        display: flex;
        justify-content: center;
        padding: var(--ks-spacing-2);
    }

    .loop-iteration-row {
        &__main {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            padding: var(--ks-spacing-1) var(--ks-spacing-4);
            padding-left: calc(var(--ks-spacing-4) + var(--depth, 0) * var(--ks-spacing-5));
            border-bottom: 1px solid var(--ks-border-default);
            cursor: pointer;
        }

        &__value {
            flex-grow: 1;
            font-family: var(--kel-font-family-monospace);
            font-size: var(--ks-font-size-sm);
        }

        &__chevron {
            flex-shrink: 0;
            color: var(--ks-icon-muted);
        }

        &__duration {
            flex-shrink: 0;
            font-family: var(--kel-font-family-monospace);
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-primary);
        }
    }

    .loop-preview-footer {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .loop-failed-filter {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: 0 var(--ks-spacing-4) var(--ks-spacing-2);
        font-size: var(--ks-font-size-sm);
        cursor: pointer;
    }

</style>
