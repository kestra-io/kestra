<template>
    <KsButton
        v-if="executions.length === 1"
        :tag="RouterLink"
        :to="subflowExecutionRoute(executions[0])"
        size="small"
        :icon="AxisYArrow"
        :aria-label="$t('open_subflow_execution', {flow: executions[0].flowId})"
        :title="$t('open_subflow_execution', {flow: executions[0].flowId})"
        data-test="subflow-execution-link"
        @click.stop
    />
    <KsDropdown v-else-if="executions.length > 1" trigger="click" @click.stop>
        <KsButton
            size="small"
            :icon="AxisYArrow"
            :aria-label="$t('sub flow')"
            :title="$t('sub flow')"
            data-test="subflow-execution-link"
        />
        <template #dropdown>
            <KsDropdownMenu>
                <KsDropdownItem
                    v-for="execution in executions"
                    :key="execution.id"
                    data-test="subflow-execution-item"
                    @click="router.push(subflowExecutionRoute(execution))"
                >
                    <KsExecutionStatus :status="execution.state" dot />
                    {{ execution.flowId }}
                </KsDropdownItem>
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {RouterLink, useRouter} from "vue-router"
    import AxisYArrow from "vue-material-design-icons/AxisYArrow.vue"
    import {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu, KsExecutionStatus} from "@kestra-io/design-system"
    import {subflowExecutionRoute, type SubflowExecutionRef} from "../../composables/useSubflowExecutions"

    defineProps<{
        executions: SubflowExecutionRef[];
    }>()

    const router = useRouter()
</script>
