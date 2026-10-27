<template>
    <KsPopover
        trigger="hover"
        placement="bottom"
        width="max-content"
        :title="$t('replayOverrides.recorded_title')"
    >
        <template #reference>
            <KsTag size="small" type="warning" data-test="task-run-overrides">
                {{ $t("replayOverrides.recorded", {count: overrides.length}, overrides.length) }}
            </KsTag>
        </template>
        <table class="overrides" data-test="task-run-overrides-detail">
            <thead>
                <tr>
                    <th>{{ $t("key") }}</th>
                    <th>{{ $t("replayOverrides.recorded_original") }}</th>
                    <th>{{ $t("replayOverrides.recorded_value") }}</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="override in overrides" :key="override.key">
                    <td><code>{{ override.key }}</code></td>
                    <td>{{ formatValue(override.originalValue) }}</td>
                    <td>{{ formatValue(override.value) }}</td>
                </tr>
            </tbody>
        </table>
    </KsPopover>
</template>

<script setup lang="ts">
    import type {TaskRunOverride} from "@kestra-io/kestra-sdk"
    import {formatValue} from "./replayOverrides"

    defineProps<{overrides: TaskRunOverride[]}>()
</script>

<style scoped>
.overrides {
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
</style>
