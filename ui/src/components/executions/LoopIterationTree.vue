<template>
    <div class="loop-iteration-tree" data-test="loop-iteration-tree">
        <GanttMessageRow v-if="loading && iterations.length === 0" :ancestors="ancestors" data-test="loop-loading">
            <KsIcon class="is-loading" size="sm">
                <Loading />
            </KsIcon>
            {{ $t("gantt_loop_loading") }}
        </GanttMessageRow>

        <GanttMessageRow v-else-if="error" :ancestors="ancestors">
            <KsAlert type="error" :closable="false">
                {{ $t("loop_iterations_load_error") }}
                <KsButton size="small" link @click="retry">{{ $t("retry") }}</KsButton>
            </KsAlert>
        </GanttMessageRow>

        <GanttMessageRow v-else-if="loaded && iterations.length === 0" :ancestors="ancestors" data-test="loop-empty">
            {{ $t(failedOnly ? "no_failed_iterations" : "no_iterations") }}
            <KsButton v-if="failedOnly && totalCount" size="small" link class="tree-link" @click="setFailedOnly(false)">
                {{ $t("gantt_loop_show_all_iterations", {count: totalCount}) }}
            </KsButton>
        </GanttMessageRow>

        <template v-for="iteration in iterationNodes" :key="iteration.key">
            <GanttRow
                :nodeKey="iteration.key"
                :label="iterationLabel(iteration.row)"
                :ancestors="ancestors"
                :offset="offset(iteration.row.state.histories)"
                :barStyle="barStyle(iteration.row.state.histories, iteration.row.state.current)"
                :state="iteration.row.state.current"
                :selected="tree.selected.value?.key === iteration.key"
                expandable
                :expanded="tree.isExpanded(iteration.key)"
                data-test="loop-iteration-row"
                @select="tree.select(iteration)"
                @toggle="tree.toggleExpanded(iteration.key)"
            >
                <template #label>
                    <span class="iteration-index">#{{ iteration.row.index + 1 }}</span>
                    <code class="iteration-value" :title="iteration.row.value">{{ iteration.row.value }}</code>
                </template>
                <template #duration>
                    <Duration :histories="iteration.row.state.histories" />
                </template>
                <template #actions>
                    <SubFlowLink
                        :executionId="iteration.row.id"
                        :namespace="loopRef.namespace"
                        :flowId="loopRef.flowId"
                        tabExecution="gantt"
                    />
                </template>
            </GanttRow>

            <IterationTaskRuns
                v-if="tree.isExpanded(iteration.key)"
                :iteration="iteration"
            />
        </template>

        <GanttMessageRow v-if="needsPreview" :ancestors="ancestors" data-test="loop-preview-footer">
            <span>{{ $t("loop_iterations_shown", {shown: iterations.length, total}) }}</span>
            <KsButton
                v-if="hasMore"
                size="small"
                link
                class="tree-link"
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
                class="tree-link"
                :to="allExecutionsRoute"
            >
                {{ $t("show_all_executions") }}
            </KsButton>
        </GanttMessageRow>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted} from "vue"
    import {RouterLink} from "vue-router"
    import Loading from "vue-material-design-icons/Loading.vue"
    import {Duration} from "@kestra-io/topology"
    import {KsAlert, KsButton, KsIcon} from "@kestra-io/design-system"
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import GanttRow from "./gantt/GanttRow.vue"
    import GanttMessageRow from "./gantt/GanttMessageRow.vue"
    import IterationTaskRuns from "./IterationTaskRuns.vue"
    import {
        createIterationNode,
        iterationLabel,
        loopAncestors,
        loopRefOf,
        useGanttTree,
        type TaskRunNode,
    } from "./gantt/ganttTree"
    import {useGanttTimeline} from "./ganttTimeline"

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const tree = useGanttTree()
    const {offset, barStyle} = useGanttTimeline()

    const loopRef = computed(() => loopRefOf(props.node))
    const ancestors = computed(() => loopAncestors(props.node))
    const {
        iterations,
        total,
        totalCount,
        loading,
        error,
        loaded,
        hasMore,
        needsPreview,
        failedOnly,
        ensureLoaded,
        loadMore,
        retry,
        setFailedOnly,
    } = tree.loopHandle(loopRef.value)

    const iterationNodes = computed(() => iterations.value.map(row => createIterationNode(loopRef.value, row, ancestors.value)))

    const allExecutionsRoute = {
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": props.node.executionId,
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": props.node.taskRun.taskId,
        },
    }

    onMounted(ensureLoaded)
</script>

<style scoped>
    .tree-link {
        color: var(--ks-text-link);
    }

    .iteration-index {
        display: grid;
        place-items: center;
        flex-shrink: 0;
        min-width: 1.75rem;
        height: 1.75rem;
        padding: 0 var(--ks-spacing-1);
        border: var(--ks-border-width-thin) dashed var(--ks-border-strong);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-base);
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
    }

    .iteration-value {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }
</style>
