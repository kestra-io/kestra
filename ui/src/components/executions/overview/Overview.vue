<template>
    <div v-if="execution" class="wrapper">
        <MobileSummary v-if="isMobile" :execution>
            <FailedTaskCard v-if="failedTaskRun" :execution />
            <ErrorAlert v-else-if="isFailed" :execution />
            <PausedAlert v-if="execution.state.current === State.PAUSED" :execution />
        </MobileSummary>
        <template v-else>
            <KsCard class="banner" shadow="always">
                <Banner :execution />
            </KsCard>
            <div id="alerts">
                <ErrorAlert
                    v-if="isFailed"
                    :execution
                />
                <PausedAlert
                    v-if="execution.state.current === State.PAUSED"
                    :execution
                />
            </div>
        </template>
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
    <KsNoData
        v-else
        id="empty"
        :description="$t('execution not found', {executionId: route.params.id})"
    />
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

    import Banner from "../components/Banner.vue"
    import FailedTaskCard from "./components/FailedTaskCard.vue"
    import MobileSummary from "./components/MobileSummary.vue"
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

    .banner {
        flex-shrink: 0;
        width: 100%;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);

        & :deep(.kel-card__body) {
            height: 100%;
            padding: 0;
        }
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
