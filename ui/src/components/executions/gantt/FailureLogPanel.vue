<template>
    <div class="failure-log-panel">
        <div v-if="loading" v-ks-loading="true" class="failure-log-panel__status" />
        <KsAlert v-else-if="error" type="error" :closable="false">
            {{ $t("failureDebugPanel.logs.error") }}
        </KsAlert>
        <KsEmpty v-else-if="visibleLogs.length === 0" :description="emptyDescription" />
        <div v-else class="failure-log-panel__lines">
            <LogLine
                v-for="(log, index) in visibleLogs"
                :key="index"
                class="line"
                :log="log"
                :excludeMetas="excludeMetas"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsAlert, KsEmpty} from "@kestra-io/design-system"
    import LogLine from "../../logs/LogLine.vue"
    import {useExecutionsStore} from "../../../stores/executions"
    import type {Log} from "../../../stores/logs"
    import type {TimeRange} from "../../../composables/useTimeRangeSelection"

    const props = defineProps<{
        executionId: string
        executionKind?: string
        taskRunId: string
        timeRange?: TimeRange
    }>()

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()

    const excludeMetas: (keyof Log)[] = ["namespace", "flowId", "taskId", "executionId"]

    const loading = ref(false)
    const error = ref(false)
    const logs = ref<Log[]>([])

    async function load() {
        loading.value = true
        error.value = false
        try {
            const params: Record<string, unknown> = {taskRunId: props.taskRunId}
            if (props.executionKind && props.executionKind !== "NORMAL") {
                params["filters[kind][IN]"] = props.executionKind
            }
            const response = await executionsStore.loadLogs({
                store: false,
                executionId: props.executionId,
                params,
                showMessageOnError: false,
            })
            logs.value = ((response as {results?: Log[]})?.results ?? response ?? []) as Log[]
        } catch {
            error.value = true
        } finally {
            loading.value = false
        }
    }

    watch(() => props.taskRunId, load, {immediate: true})

    const visibleLogs = computed(() => {
        if (!props.timeRange) return logs.value
        const {start, end} = props.timeRange
        return logs.value.filter((log) => {
            const timestamp = new Date(log.timestamp).getTime()
            return timestamp >= start && timestamp <= end
        })
    })

    const emptyDescription = computed(() =>
        props.timeRange && logs.value.length > 0
            ? t("failureDebugPanel.logs.emptyFiltered")
            : t("failureDebugPanel.logs.empty"),
    )

    defineExpose({reload: load})
</script>

<style scoped lang="scss">
    .failure-log-panel {
        min-height: 4rem;
    }

    .failure-log-panel__status {
        position: relative;
        min-height: 4rem;
    }

    .failure-log-panel__lines {
        display: flex;
        flex-direction: column;

        .line {
            padding: var(--ks-spacing-1) 0;
            border-top: 1px solid var(--ks-border-default);

            &:first-child {
                border-top: none;
            }
        }
    }
</style>
