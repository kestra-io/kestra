<template>
    <div v-if="hasLoopIterations" class="loop-iterations-notice" data-test="loop-iterations-notice">
        <KsAlert type="info" :closable="false">
            <div class="notice-body">
                <span>{{ $t(`loop_iterations_notice.${props.kind}`) }}</span>

                <KsButton :tag="RouterLink" :to="iterationsRoute" size="small" link>
                    {{ $t("loop_iterations_notice.view_iterations") }}
                </KsButton>
            </div>
        </KsAlert>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {RouterLink} from "vue-router"

    import {useExecutionsStore} from "../../stores/executions"
    import {loopIterationsRoute} from "../../utils/executionUtils"
    import {loopTaskIds} from "../../utils/flowUtils"

    const props = defineProps<{
        kind: "logs" | "outputs" | "metrics";
        taskId?: string;
    }>()

    const executionsStore = useExecutionsStore()

    const loopIds = computed(() => loopTaskIds(executionsStore.flow))

    const hasLoopIterations = computed(() =>
        (executionsStore.execution?.taskRunList ?? []).some((taskRun) => loopIds.value.has(taskRun.taskId)),
    )

    const iterationsRoute = computed(() => loopIterationsRoute(executionsStore.execution, props.taskId))
</script>

<style scoped>
    .loop-iterations-notice {
        margin-bottom: var(--ks-spacing-4);
    }

    .notice-body {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-4);
    }
</style>
