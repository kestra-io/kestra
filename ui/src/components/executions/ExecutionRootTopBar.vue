<template>
    <TopNavBar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb" :bookmarkLabel="routeInfo?.bookmarkLabel">
        <template #title>
            {{ isMobile && execution ? execution.flowId : routeInfo?.title }}
            <Badge
                v-if="isATestExecution"
                :label="$t('test-badge-text')"
                :tooltip="$t('test-badge-tooltip')"
            />
        </template>
        <template #actions>
            <ReviewLink v-if="execution" :execution="execution" />
            <template v-if="execution && isMobile">
                <NavBarActionsDropdown>
                    <component
                        :is="item.component"
                        v-for="item in overflowItems"
                        :key="item.key"
                        v-bind="item.props"
                        :execution="execution"
                    />

                    <TriggerFlow
                        v-if="isAllowedTrigger"
                        :flowId="execution.flowId"
                        :namespace="execution.namespace"
                    >
                        <template #button="{execute, disabled}">
                            <NavBarAction :icon="Play" :label="$t('execute')" :disabled="disabled" @click="execute" />
                        </template>
                    </TriggerFlow>

                    <component :is="ACTIONS.delete.component" :execution="execution" />
                </NavBarActionsDropdown>

                <BottomActionBar>
                    <slot name="secondary">
                        <component
                            :is="ACTIONS[secondaryKey].component"
                            v-bind="ACTIONS[secondaryKey].props ?? {}"
                            :execution="execution"
                        />
                    </slot>
                    <component
                        :is="ACTIONS.replay.component"
                        v-if="showReplayInBottomBar"
                        v-bind="ACTIONS.replay.props ?? {}"
                        :execution="execution"
                    />
                </BottomActionBar>
            </template>
            <NavBarActions v-else-if="execution">
                <component
                    :is="item.component"
                    v-for="item in overflowItems"
                    :key="item.key"
                    v-bind="item.props"
                    :execution="execution"
                />

                <component :is="ACTIONS.delete.component" :execution="execution" />

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
    import {useIsMobile} from "../../composables/useIsMobile"
    import {State, type KsBreadcrumbItem} from "@kestra-io/design-system"

    import Badge from "../global/Badge.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import NavBarAction from "../layout/NavBarAction.vue"
    import NavBarActions from "../layout/NavBarActions.vue"
    import NavBarActionsDropdown from "../layout/NavBarActionsDropdown.vue"
    import BottomActionBar from "../layout/BottomActionBar.vue"
    import Play from "vue-material-design-icons/Play.vue"
    import TriggerFlow from "../flows/TriggerFlow.vue"
    import Api from "./overview/components/actions/Api.vue"
    import Delete from "./overview/components/actions/Delete.vue"
    import EditFlow from "./overview/components/actions/EditFlow.vue"
    import ForceRun from "./overview/components/actions/ForceRun.vue"
    import Kill from "./overview/components/actions/Kill.vue"
    import Pause from "./overview/components/actions/Pause.vue"
    import Restart from "./overview/components/actions/Restart.vue"
    import Resume from "./overview/components/actions/Resume.vue"
    import Review from "./overview/components/actions/Review.vue"
    import ReviewLink from "./ReviewLink.vue"
    import CancelApproval from "./overview/components/actions/CancelApproval.vue"
    import {findApprovalTask, findApprovalTaskRun} from "../../utils/approval"
    import * as ExecutionUtils from "../../utils/executionUtils"
    import ResumeFromBreakpoint from "./overview/components/actions/ResumeFromBreakpoint.vue"
    import Unqueue from "./overview/components/actions/Unqueue.vue"
    import action from "../../models/action"
    import resource from "../../models/resource"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {overflowActionComponents} from "override/components/executions/executionsExtensions"

    defineProps<{
        routeInfo: {
            title: string;
            breadcrumb?: KsBreadcrumbItem[];
            bookmarkLabel?: string;
        };
    }>()

    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()
    const isMobile = useIsMobile()

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
        | "review"
        | "cancelApproval"
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
        review: {component: Review},
        cancelApproval: {component: CancelApproval},
        resumeFromBreakpoint: {component: ResumeFromBreakpoint},
        unqueue: {component: Unqueue},
        forceRun: {component: ForceRun},
        api: {component: Api},
        editFlow: {component: EditFlow},
        delete: {component: Delete},
    }

    const executionFlowLoaded = computed(() => {
        const flow = executionsStore.flow
        return execution.value !== undefined && flow !== undefined
            && flow.id === execution.value.flowId && flow.namespace === execution.value.namespace && flow.revision === execution.value.flowRevision
    })

    const pausedOnApproval = computed(() =>
        execution.value !== undefined && findApprovalTaskRun(execution.value, executionsStore.flow, "PAUSED") !== undefined,
    )

    const pausedBesideApproval = computed(() =>
        execution.value !== undefined && executionsStore.flow !== undefined
        && ExecutionUtils.findTaskRunsByState(execution.value, "PAUSED").some(taskRun => findApprovalTask(executionsStore.flow, taskRun.taskId) === undefined),
    )

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
            if (!executionFlowLoaded.value) {
                return "editFlow"
            }
            return pausedOnApproval.value ? "review" : "resume"
        }

        if ((current as string) === State.PAUSING) {
            return "editFlow"
        }

        if (State.isRunning(current)) {
            return "pause"
        }

        if (current === State.FAILED) {
            return "restart"
        }

        if (State.getNonRunningStates().includes(current)) {
            return "replay"
        }

        return "editFlow"
    })

    function pauseKeys(): ActionKey[] {
        const current = execution.value?.state?.current
        if ((current as string) === State.PAUSING) {
            return []
        }
        if (current !== "PAUSED") {
            return ["pause"]
        }
        if (!executionFlowLoaded.value) {
            return []
        }
        if (!pausedOnApproval.value) {
            return ["resume"]
        }
        return ["review", "cancelApproval", ...(pausedBesideApproval.value ? ["resume" as const] : [])]
    }

    const overflowKeys = computed<ActionKey[]>(() => {
        const keys: ActionKey[] = [
            "restart",
            "replay",
            "kill",
            ...pauseKeys(),
            "resumeFromBreakpoint",
            "unqueue",
            "forceRun",
            "api",
            "editFlow",
        ]
        return keys.filter((key) => key !== secondaryKey.value)
    })

    const showReplayInBottomBar = computed(() => isMobile.value && secondaryKey.value === "restart")

    const overflowItems = computed(() => [
        ...overflowKeys.value
            .filter((key) => !(key === "replay" && showReplayInBottomBar.value))
            .map((key) => ({key, component: ACTIONS[key].component, props: ACTIONS[key].props ?? {}})),
        ...overflowActionComponents.map((component, index) => ({key: `extra-${index}`, component, props: {}})),
    ])

    const isATestExecution = computed(() =>
        execution.value?.labels?.some(
            (label: {key: string; value: string}) => label.key === "system.test" && label.value === "true",
        ) ?? false,
    )
</script>
