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
    import {loopTaskIds} from "../../utils/flowUtils"

    const props = defineProps<{
        kind: "logs" | "outputs" | "metrics";
        taskId?: string;
    }>()

    const executionsStore = useExecutionsStore()

    const hasLoopIterations = computed(() => {
        const ids = loopTaskIds(executionsStore.flow)
        return (executionsStore.execution?.taskRunList ?? []).some((taskRun) => ids.has(taskRun.taskId))
    })

    const iterationsRoute = computed(() => ({
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": executionsStore.execution?.id,
            "filters[kind][EQUALS]": "LOOP",
            ...(props.taskId ? {"filters[taskId][EQUALS]": props.taskId} : {}),
        },
    }))
</script>

<style scoped lang="scss">
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
