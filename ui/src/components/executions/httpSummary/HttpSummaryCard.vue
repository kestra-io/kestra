<template>
    <div class="http-card" data-test="http-summary-card">
        <KsSkeleton v-if="loading && !summary" :rows="3" animated data-test="http-summary-loading" />
        <KsText v-else-if="failed" type="danger" size="small" data-test="http-summary-error">
            {{ $t("http_summary.load_error") }}
        </KsText>
        <KsText v-else-if="!summary" size="small" data-test="http-summary-empty">
            {{ $t("http_summary.no_outputs") }}
        </KsText>
        <KsTopologyDetails v-else :rows="rows" data-test="http-summary-rows" />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsSkeleton, KsText, KsTopologyDetails} from "@kestra-io/design-system"
    import type {Execution, Task} from "@kestra-io/kestra-sdk"
    import {humanFileSize} from "../../../utils/utils"
    import {formatMillis, httpTaskKind} from "./httpSummary"
    import {taskRunRefreshKey, useHttpSummary, type FetchOutputs} from "./useHttpSummary"

    const props = defineProps<{
        task: Task
        execution?: Execution
        fetchOutputs?: FetchOutputs
    }>()

    const {t} = useI18n()

    const {summary, loading, failed} = useHttpSummary(
        () => props.fetchOutputs,
        () => httpTaskKind(props.task.type),
        () => taskRunRefreshKey(props.execution, props.task.id),
    )

    const rows = computed(() => {
        const value = summary.value
        if (!value) return []
        const target = value.host ? `${value.host}${value.path ?? ""}` : undefined
        const entries: [string, string | undefined][] = [
            [t("http_summary.method"), value.method],
            [t("http_summary.target"), target],
            [t("http_summary.status"), value.code === undefined ? undefined : [value.code, value.reason].filter(Boolean).join(" ")],
            [t("http_summary.content_type"), value.contentType],
            [t("http_summary.size"), value.bytes === undefined ? undefined : humanFileSize(value.bytes)],
            [t("http_summary.duration"), value.durationMs === undefined ? undefined : formatMillis(value.durationMs)],
        ]
        return entries.flatMap(([label, text]) => text ? [{label, value: text}] : [])
    })
</script>

<style lang="scss" scoped>
    .http-card {
        padding: var(--ks-spacing-2);
    }
</style>
