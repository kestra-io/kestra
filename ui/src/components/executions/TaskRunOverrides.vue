<template>
    <KsPopover
        trigger="hover"
        placement="bottom"
        width="max-content"
        :title="$t('replayOverrides.recorded_title')"
        @show="load"
    >
        <template #reference>
            <KsTag size="small" type="warning" data-test="task-run-overrides">
                {{ $t("replayOverrides.recorded", {count: overrides.keys.length}, overrides.keys.length) }}
            </KsTag>
        </template>
        <div v-ks-loading="loading" class="overrides" data-test="task-run-overrides-detail">
            <KsText v-if="overrides.editedBy" tag="p" size="small" type="info">
                {{ $t("replayOverrides.recorded_by", {user: overrides.editedBy, date: dateFilter(overrides.editedAt)}) }}
            </KsText>
            <table>
                <thead>
                    <tr>
                        <th>{{ $t("key") }}</th>
                        <th v-if="values">{{ $t("replayOverrides.recorded_original") }}</th>
                        <th v-if="values">{{ $t("replayOverrides.recorded_value") }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="key in overrides.keys" :key="key">
                        <td><code>{{ key }}</code></td>
                        <template v-if="values">
                            <td>{{ formatValue(values[key]?.originalValue) }}</td>
                            <td>{{ formatValue(values[key]?.value) }}</td>
                        </template>
                    </tr>
                </tbody>
            </table>
            <KsText v-if="hidden" tag="p" size="small" type="info" data-test="task-run-overrides-hidden">
                {{ $t("replayOverrides.recorded_hidden") }}
            </KsText>
        </div>
    </KsPopover>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import type {TaskRunOverride, TaskRunOverrides} from "@kestra-io/kestra-sdk"
    import {asProblem} from "@kestra-io/kestra-sdk"
    import {useExecutionsStore} from "../../stores/executions"
    import {handled} from "../../utils/kestraHttp"
    import {date as dateFilter} from "../../utils/filters"
    import {formatValue} from "./replayOverrides"

    const props = defineProps<{
        executionId: string
        taskRunId: string
        overrides: TaskRunOverrides
    }>()

    const executionsStore = useExecutionsStore()

    const loading = ref(false)
    const hidden = ref(false)
    const values = ref<Record<string, TaskRunOverride>>()

    async function load() {
        if (values.value || hidden.value || loading.value) return
        loading.value = true
        try {
            const overrides = await executionsStore.loadTaskRunOverrides({executionId: props.executionId, taskRunId: props.taskRunId})
            values.value = Object.fromEntries(overrides.map(override => [override.key, override]))
        } catch (error) {
            if (asProblem(error)?.status !== 403) throw error
            handled(error)
            hidden.value = true
        } finally {
            loading.value = false
        }
    }
</script>

<style scoped>
.overrides {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
}

.overrides table {
    border-collapse: collapse;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-primary);
}

.overrides th {
    text-align: start;
    font-weight: 600;
    color: var(--ks-text-secondary);
}

.overrides th,
.overrides td {
    padding: var(--ks-spacing-1) var(--ks-spacing-2);
    border-bottom: 1px solid var(--ks-border-subtle);
}

.overrides p {
    margin: 0;
}
</style>
