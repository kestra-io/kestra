<template>
    <div v-if="execution" class="wrapper">
        <ExecutionSummaryCard class="overview-summary" :execution>
            <FailedTaskCard v-if="failedTaskRun" :execution />
            <ErrorAlert v-else-if="isFailed" :execution />
            <PausedAlert v-if="execution.state.current === State.PAUSED" :execution />
        </ExecutionSummaryCard>
        <div v-if="!isMobile" id="alerts">
            <ErrorAlert
                v-if="isFailed"
                :execution
            />
            <PausedAlert
                v-if="execution.state.current === State.PAUSED"
                :execution
            />
        </div>
        <div v-if="isMobile && failedTaskRun" class="topology-heading">
            <span>{{ $t("topology") }}</span>
            <KsButton link type="primary" data-test="jump-to-first-failure" @click="jumpToFirstFailure">
                {{ $t("execution_mobile.jump_to_first_failure") }}
                <ArrowRight />
            </KsButton>
        </div>
        <Topology
            ref="topologyRef"
            class="topology"
            :horizontalDefault="!isMobile"
        />
        <PrevNext :execution />
    </div>
    <KsNoData v-else id="empty">
        {{ $t("execution not found", {executionId: route.params.id}) }}
    </KsNoData>
</template>

<script setup lang="ts">
    import {onMounted, computed, useTemplateRef} from "vue"

    import {useRoute} from "vue-router"
    const route = useRoute()

    const isMobile = useIsMobile()

    import {useExecutionsStore} from "../../../stores/executions"
    const store = useExecutionsStore()

    import {State} from "@kestra-io/design-system"

    import ArrowRight from "vue-material-design-icons/ArrowRight.vue"

    import ExecutionSummaryCard from "../components/ExecutionSummaryCard.vue"
    import FailedTaskCard from "./components/FailedTaskCard.vue"
    import ErrorAlert from "./components/main/ErrorAlert.vue"
    import PausedAlert from "./components/main/PausedAlert.vue"
    import PrevNext from "./components/main/PrevNext.vue"
    import Topology from "../Topology.vue"
    import {useIsMobile} from "../../../composables/useIsMobile"
    import {useFirstFailure} from "../../../composables/useFirstFailure"

    const execution = computed(() => store.execution)

    const topologyRef = useTemplateRef<InstanceType<typeof Topology>>("topologyRef")

    const isFailed = computed(() => execution.value?.state.current === State.FAILED)

    const {failedTaskRun} = useFirstFailure(execution, false)

    function jumpToFirstFailure() {
        const taskId = failedTaskRun.value?.taskId
        if (!taskId) return
        const topologyElement = (topologyRef.value?.$el ?? undefined) as HTMLElement | undefined
        topologyElement?.scrollIntoView({block: "start", behavior: "smooth"})
        topologyRef.value?.focusTask(taskId)
    }

    const loadExecution = (id: string) => store.loadExecution({id})

    onMounted(() => {
        if (!route.params.id) return
        // The route guard loads the execution into the store before this page mounts
        // (EXECUTION_ENTITY_META), and the SSE stream keeps it current from there.
        if (execution.value?.id === route.params.id) return
        loadExecution(route.params.id as string)
    })

    defineOptions({inheritAttrs: false})
</script>

<style scoped>
    .wrapper {
        display: flex;
        flex-direction: column;
        height: 100%;
        gap: var(--ks-spacing-4);
        min-width: 0;
    }

    .overview-summary {
        padding-bottom: 0;
    }

    #alerts {
        min-width: 0;
    }

    #alerts:empty {
        display: none;
    }

    .topology-heading {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
        color: var(--ks-text-secondary);
    }

    .topology {
        flex: 1;
        /* Floor so a short viewport still gets a usable graph; the page scrolls from there. */
        min-height: 400px;
    }

    #empty {
        height: 100%;
        background-color: var(--ks-bg-elevated);
    }
</style>
