<template>
    <div v-if="hasLoopIterations" class="loop-iterations-notice">
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
    import {loopOver} from "../../utils/flowUtils"

    const LOOP_TASK_TYPE = "io.kestra.plugin.core.flow.Loop"

    const props = defineProps<{
        kind: "logs" | "outputs" | "metrics";
    }>()

    const executionsStore = useExecutionsStore()

    const loopTaskIds = computed(() => new Set(
        loopOver(executionsStore.flow, (task) => task?.type === LOOP_TASK_TYPE).map((task) => task.id),
    ))

    const hasLoopIterations = computed(() =>
        (executionsStore.execution?.taskRunList ?? []).some((taskRun) => loopTaskIds.value.has(taskRun.taskId)),
    )

    const iterationsRoute = computed(() => ({
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": executionsStore.execution?.id,
            "filters[kind][EQUALS]": "LOOP",
        },
    }))
</script>

<style scoped lang="scss">
    /* Owns the gap under itself so no host has to space the alert out. */
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
