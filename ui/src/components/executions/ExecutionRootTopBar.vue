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
            <NavBarActions v-if="execution">
                <component
                    :is="ACTIONS[key].component"
                    v-for="key in overflowKeys"
                    :key="key"
                    v-bind="ACTIONS[key].props ?? {}"
                    :execution="execution"
                />

                <!--
                    A tab with its own more specific action (Audit Logs exporting to CSV in the
                    Enterprise Edition) contributes it here; every other tab falls back to the
                    action that matches the execution's state.

                    Callers MUST gate the `<template #secondary>` declaration itself, e.g.
                    `<template #secondary v-if="activeTab === 'audit-logs'">`. Putting the `v-if`
                    on the content inside the slot instead would keep the slot declared on every
                    tab and blank the secondary, losing Replay / Restart / Pause / Resume.
                -->
                <template #secondary>
                    <slot name="secondary">
                        <component
                            :is="ACTIONS[secondaryKey].component"
                            v-bind="ACTIONS[secondaryKey].props ?? {}"
                            :execution="execution"
                        />
                    </slot>
                </template>

                <template #primary>
                    <TriggerFlow
                        v-if="isAllowedTrigger"
                        type="primary"
                        :flowId="execution?.flowId"
                        :namespace="execution?.namespace"
                    />
                </template>
            </NavBarActions>
        </template>
    </TopNavBar>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {State} from "@kestra-io/design-system"

    import Badge from "../global/Badge.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import NavBarActions from "../layout/NavBarActions.vue"
    import TriggerFlow from "../flows/TriggerFlow.vue"
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

    type ActionKey =
        | "restart"
        | "replay"
        | "kill"
        | "pause"
        | "resume"
        | "resumeFromBreakpoint"
        | "unqueue"
        | "forceRun"
        | "api"
        | "editFlow"
        | "delete"

    const ACTIONS: Record<ActionKey, {component: Component; props?: Record<string, unknown>}> = {
        restart: {component: Restart},
        replay: {component: Restart, props: {isReplay: true}},
        kill: {component: Kill},
        pause: {component: Pause},
        resume: {component: Resume},
        resumeFromBreakpoint: {component: ResumeFromBreakpoint},
        unqueue: {component: Unqueue},
        forceRun: {component: ForceRun},
        api: {component: Api},
        editFlow: {component: EditFlow},
        delete: {component: Delete},
    }

    /**
     * The single visible secondary action: the one action that only makes sense for the
     * execution's current state, falling back to Edit Flow. Execute owns the primary slot on
     * every tab, so replaying is one click away without being what a user hits by reflex.
     */
    const secondaryKey = computed<ActionKey>(() => {
        const current = execution.value?.state?.current

        if (!current) {
            return "editFlow"
        }

        if (current === "BREAKPOINT") {
            return "resumeFromBreakpoint"
        }

        if (State.isPaused(current)) {
            return "resume"
        }

        if (State.isRunning(current)) {
            return "pause"
        }

        if (current === State.FAILED) {
            return "restart"
        }

        if (State.getTerminatedStates().includes(current)) {
            return "replay"
        }

        return "editFlow"
    })

    const overflowKeys = computed<ActionKey[]>(() => {
        const isPaused = execution.value?.state?.current === "PAUSED"
        const keys: ActionKey[] = [
            "restart",
            "replay",
            "kill",
            isPaused ? "resume" : "pause",
            "resumeFromBreakpoint",
            "unqueue",
            "forceRun",
            "api",
            "editFlow",
            "delete",
        ]
        return keys.filter((key) => key !== secondaryKey.value)
    })

    const isATestExecution = computed(() =>
        execution.value?.labels?.some(
            (label: {key: string; value: string}) => label.key === "system.test" && label.value === "true",
        ) ?? false,
    )
</script>
