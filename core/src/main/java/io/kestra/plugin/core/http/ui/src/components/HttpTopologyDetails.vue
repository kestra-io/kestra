<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { KnownSlotProps } from "@kestra-io/artifact-sdk";
import { KsSkeleton, KsText, KsTopologyDetails } from "@kestra-io/design-system";
import { formatBytes, formatMillis, formatStatus, httpTaskKind } from "./httpSummary";
import { messages } from "./messages";
import { taskRunRefreshKey, useHttpSummary } from "./useHttpSummary";

const { t } = useI18n({ inheritLocale: true, useScope: "local", messages });

const props = defineProps<KnownSlotProps["topology-details"]>();

const { summary, loading, failed } = useHttpSummary(
    () => props.fetchOutputs,
    () => httpTaskKind(props.task.type),
    () => taskRunRefreshKey(props.execution, props.task.id),
);

const rows = computed(() => {
    const value = summary.value;
    if (!value) return [];
    const entries: [string, string | undefined][] = [
        [t("method"), value.method],
        [t("target"), value.host ? `${value.host}${value.path ?? ""}` : undefined],
        [t("status"), formatStatus(value.code, value.reason)],
        [t("contentType"), value.contentType],
        [t("size"), value.bytes === undefined ? undefined : formatBytes(value.bytes)],
        [
            t("duration"),
            value.durationMs === undefined ? undefined : formatMillis(value.durationMs),
        ],
    ];
    return entries.flatMap(([label, text]) => (text ? [{ label, value: text }] : []));
});
</script>

<template>
    <div class="http-card" data-test="http-summary-card">
        <KsSkeleton
            v-if="loading && !summary"
            :rows="3"
            animated
            data-test="http-summary-loading"
        />
        <KsText v-else-if="failed" type="danger" size="small" data-test="http-summary-error">
            {{ t("loadError") }}
        </KsText>
        <KsText v-else-if="!summary" size="small" data-test="http-summary-empty">
            {{ t("noOutputs") }}
        </KsText>
        <KsTopologyDetails v-else :rows="rows" data-test="http-summary-rows" />
    </div>
</template>

<style scoped>
.http-card {
    padding: var(--ks-spacing-2);
}
</style>