<template>
    <KsButton
        v-if="children.length === 1"
        :tag="RouterLink"
        :to="subflowExecutionRoute(children[0])"
        link
        size="small"
        :icon="AxisYArrow"
        class="subflow-execution-link"
        :aria-label="$t('open_subflow_execution', {flow: children[0].flowId})"
        :title="$t('open_subflow_execution', {flow: children[0].flowId})"
        data-test="gantt-subflow-execution"
    >
        {{ children[0].flowId }}
    </KsButton>
    <SubflowExecutionsLink v-else-if="children.length > 1" :executions="children" />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {RouterLink} from "vue-router"
    import AxisYArrow from "vue-material-design-icons/AxisYArrow.vue"
    import {KsButton} from "@kestra-io/design-system"
    import SubflowExecutionsLink from "../SubflowExecutionsLink.vue"
    import {isSubflowTaskType} from "../../../services/subflowReferences"
    import {subflowExecutionRoute, useSubflowExecutions} from "../../../composables/useSubflowExecutions"
    import type {TaskRunNode} from "./ganttTree"

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const {subflowExecutionsOf} = useSubflowExecutions(() => isSubflowTaskType(props.node.taskType)
        ? [{id: props.node.executionId, state: props.node.taskRun.state}]
        : [])
    const children = computed(() => subflowExecutionsOf(props.node.executionId)
        .filter(child => child.taskRunId === props.node.taskRun.id))
</script>

<style scoped>
    .subflow-execution-link {
        min-width: 0;
        font-family: var(--ks-font-family-mono);
    }
</style>
