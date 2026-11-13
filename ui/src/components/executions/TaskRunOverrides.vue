<template>
    <KsPopover
        trigger="hover"
        placement="bottom-end"
        width="max-content"
        :title="$t('replayOverrides.recorded_title')"
        @show="load"
    >
        <template #reference>
            <KsTag size="small" type="warning" data-test="task-run-overrides">
                {{ $t("replayOverrides.recorded", {count: overrides.paths.length}, overrides.paths.length) }}
            </KsTag>
        </template>
        <div v-ks-loading="loading" class="overrides" data-test="task-run-overrides-detail">
            <KsText v-if="overrides.overriddenBy" tag="p" size="small" type="info">
                {{ $t("replayOverrides.recorded_by", {user: overrides.overriddenBy, date: dateFilter(overrides.overriddenAt)}) }}
            </KsText>
            <table>
                <thead>
                    <tr>
                        <th>{{ $t("replayOverrides.recorded_path") }}</th>
                        <th v-if="values">{{ $t("replayOverrides.recorded_original") }}</th>
                        <th v-if="values">{{ $t("replayOverrides.recorded_value") }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="path in overrides.paths" :key="path">
                        <td><code>{{ path }}</code></td>
                        <template v-if="values">
                            <td>{{ formatValue(values[path]?.originalValue) }}</td>
                            <td>{{ formatValue(values[path]?.value) }}</td>
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
    import type {ExecutionControllerOverriddenOutput, TaskRunOverrides} from "@kestra-io/kestra-sdk"
    import {asProblem} from "@kestra-io/kestra-sdk"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
    import {handled} from "../../utils/kestraHttp"
    import {date as dateFilter} from "../../utils/filters"
    import {formatValue} from "./replayOverrides"

    const props = defineProps<{
        executionId: string
        taskRunId: string
        overrides: TaskRunOverrides
    }>()

    const loading = ref(false)
    const hidden = ref(false)
    const values = ref<Record<string, ExecutionControllerOverriddenOutput>>()

    async function load() {
        if (values.value || hidden.value || loading.value) return
        loading.value = true
        try {
            const {values: overrides} = await ExecutionsAPI.taskRunOverrides({executionId: props.executionId, taskRunId: props.taskRunId})
            values.value = Object.fromEntries(overrides.map(override => [override.path, override]))
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
