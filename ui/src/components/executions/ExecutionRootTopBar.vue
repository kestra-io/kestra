<template>
    <TopNavBar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #title>
            {{ routeInfo?.title }}
            <Badge
                v-if="isATestExecution"
                :label="$t('test-badge-text')"
                :tooltip="$t('test-badge-tooltip')"
            />
        </template>
        <template #actions>
            <slot name="actions" />
            <div
                v-if="hasVisibleActions && $route.params.tab !== 'audit-logs'"
                class="d-flex align-items-center gap-2"
            >
                <ExecutionActions
                    v-if="execution && executionActions.length"
                    :actions="executionActions"
                    :execution
                />

                <div
                    v-if="primaryAction || fallbackToExecute"
                    class="d-flex align-items-center gap-2"
                >
                    <component
                        v-if="primaryAction"
                        :is="primaryAction.component"
                        v-bind="primaryAction.props"
                        :execution="execution"
                        type="primary"
                    />

                    <TriggerFlow
                        v-else-if="fallbackToExecute"
                        type="primary"
                        :flowId="$route.params.flowId as string"
                        :namespace="$route.params.namespace as string"
                    />
                </div>
            </div>
        </template>
    </TopNavBar>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {State} from "@kestra-io/design-system"

    import Badge from "../global/Badge.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import TriggerFlow from "../flows/TriggerFlow.vue"
    import ExecutionActions from "./ExecutionActions.vue"
    import Api from "./overview/components/actions/Api.vue"
    import Delete from "./overview/components/actions/Delete.vue"
    import EditFlow from "./overview/components/actions/EditFlow.vue"
    import ForceRun from "./overview/components/actions/ForceRun.vue"
    import Kill from "./overview/components/actions/Kill.vue"
    import Pause from "./overview/components/actions/Pause.vue"
    import Restart from "./overview/components/actions/Restart.vue"
    import Resume from "./overview/components/actions/Resume.vue"
    import ResumeFromBreakpoint from "./overview/components/actions/ResumeFromBreakpoint.vue"
    import Unqueue from "./overview/components/actions/Unqueue.vue"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"

    defineProps<{
        // FIXME: any - routeInfo shape varies across usage
        routeInfo: any // FIXME: any
    }>()

    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const execution = computed(() => executionsStore.execution)

    const isAllowedTrigger = computed(() =>
        execution.value && authStore.user?.isAllowed(resource.FLOW, action.EXECUTE, execution.value.namespace),
    )

    const primaryAction = computed(() => {
        if (!execution.value?.state) {
            return null
        }

        if (execution.value.state.current === "BREAKPOINT") {
            return {component: ResumeFromBreakpoint, props: {}}
        }

        if (State.isPaused(execution.value.state.current)) {
            return {component: Resume, props: {}}
        }

        if (State.isRunning(execution.value.state.current)) {
            return {component: Pause, props: {}}
        }

        if (execution.value.state.current === State.FAILED) {
            return {component: Restart, props: {}}
        }

        if (State.getTerminatedStates().includes(execution.value.state.current)) {
            return {component: Restart, props: {isReplay: true}}
        }

        return null
    })

    const fallbackToExecute = computed(() =>
        execution.value && isAllowedTrigger.value && !primaryAction.value,
    )

    const executionActions = computed(() => {
        if (!execution.value?.state) return []
        return [
            {component: EditFlow},
            {component: Restart},
            {component: Restart, props: {isReplay: true}},
            {component: Kill},
            execution.value.state.current !== "PAUSED"
                ? {component: Pause}
                : {component: Resume},
            {component: ResumeFromBreakpoint},
            {component: Unqueue},
            {component: ForceRun},
            {component: Api},
            {component: Delete},
        ]
    })

    const hasVisibleActions = computed(() =>
        primaryAction.value ||
        fallbackToExecute.value ||
        executionActions.value.length > 0,
    )

    const isATestExecution = computed(() =>
        execution.value?.labels?.some(
            (label: {key: string; value: string}) => label.key === "system.test" && label.value === "true",
        ) ?? false,
    )
</script>
