<template>
    <div class="subflow-execution-tree" data-test="subflow-execution-tree">
        <GanttMessageRow v-if="!entry.children && entry.error" :ancestors="ancestors">
            <KsAlert type="error" :closable="false">
                {{ $t("gantt_subflow.load_error") }}
                <KsButton size="small" link @click="tree.loadSubflowExecutions(node.executionId, true)">{{ $t("retry") }}</KsButton>
            </KsAlert>
        </GanttMessageRow>
        <GanttMessageRow v-else-if="!entry.children" :ancestors="ancestors">
            <KsIcon class="is-loading" size="sm">
                <Loading />
            </KsIcon>
            {{ $t("gantt_subflow.loading") }}
        </GanttMessageRow>
        <GanttMessageRow v-else-if="children.length === 0" :ancestors="ancestors" data-test="subflow-empty">
            {{ $t("gantt_subflow.none") }}
        </GanttMessageRow>
        <ExecutionTaskRuns
            v-else-if="children.length === 1"
            :key="children[0].id"
            :execution="children[0]"
            :ancestors="ancestors"
            subflow
        />
        <template v-else>
            <template v-for="child in children" :key="child.id">
                <GanttRow
                    :nodeKey="executionKey(child.id)"
                    :label="child.flowId"
                    :ancestors="ancestors"
                    :offset="offset(child.state.histories)"
                    :barStyle="barStyle(child.state.histories, child.state.current)"
                    :state="child.state.current"
                    :selected="false"
                    expandable
                    :expanded="tree.isExpanded(executionKey(child.id))"
                    data-test="subflow-execution-row"
                    @select="tree.toggleExpanded(executionKey(child.id))"
                    @toggle="tree.toggleExpanded(executionKey(child.id))"
                >
                    <template #label>
                        <code class="execution-flow">{{ child.flowId }}</code>
                        <KsId :value="child.id" />
                    </template>
                    <template #duration>
                        <Duration :histories="child.state.histories" />
                    </template>
                    <template #actions>
                        <GanttOpenExecution :execution="child" iconOnly />
                    </template>
                </GanttRow>
                <ExecutionTaskRuns
                    v-if="tree.isExpanded(executionKey(child.id))"
                    :execution="child"
                    :ancestors="[...ancestors, {key: executionKey(child.id), label: child.flowId}]"
                    subflow
                />
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, watch} from "vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import {Duration} from "@kestra-io/topology"
    import {KsAlert, KsButton, KsIcon, KsId} from "@kestra-io/design-system"
    import ExecutionTaskRuns from "./ExecutionTaskRuns.vue"
    import GanttRow from "./gantt/GanttRow.vue"
    import GanttMessageRow from "./gantt/GanttMessageRow.vue"
    import GanttOpenExecution from "./gantt/GanttOpenExecution.vue"
    import {executionKey, loopAncestors, subflowChildren, useGanttTree, type TaskRunNode} from "./gantt/ganttTree"
    import {useGanttTimeline} from "./ganttTimeline"

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const tree = useGanttTree()
    const {offset, barStyle} = useGanttTimeline()

    const entry = tree.subflowExecutions(props.node.executionId)
    const children = computed(() => subflowChildren(tree, props.node))
    const ancestors = computed(() => loopAncestors(props.node))

    onMounted(() => tree.loadSubflowExecutions(props.node.executionId))

    // A running Subflow task starts its execution after the row exists, and that execution keeps moving until the task ends.
    watch(() => props.node.taskRun.state.current, async () => {
        await tree.loadSubflowExecutions(props.node.executionId, true)
        children.value.forEach(child => tree.loadExecution(child.id, true))
    })
</script>

<style scoped>
    .subflow-execution-tree {
        display: flex;
        flex-direction: column;
    }

    .execution-flow {
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);
    }
</style>
