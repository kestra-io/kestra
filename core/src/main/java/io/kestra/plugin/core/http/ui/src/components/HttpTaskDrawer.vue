<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type { KnownSlotProps } from "@kestra-io/artifact-sdk";
import {
    KsAlert,
    KsButton,
    KsEmpty,
    KsLink,
    KsSkeleton,
    KsTable,
    KsTableColumn,
    KsTag,
    KsText,
} from "@kestra-io/design-system";
import {
    BODY_PREVIEW_LIMIT,
    executionFileUrl,
    downloadFileName,
    formatBytes,
    formatMillis,
    formatStatus,
    formatTimeout,
    httpTaskKind,
} from "./httpSummary";
import { messages } from "./messages";
import { taskRunRefreshKey, useHttpSummary } from "./useHttpSummary";

const { t } = useI18n({ inheritLocale: true, useScope: "local", messages });

const props = defineProps<KnownSlotProps["topology-task-drawer"]>();

const { summary, loading, failed, reload } = useHttpSummary(
    () => props.fetchOutputs,
    () => httpTaskKind(props.task.type),
    () => taskRunRefreshKey(props.execution, props.task.id),
);

const fileUrl = computed(() =>
    summary.value?.fileUri
        ? executionFileUrl(props.execution?.id, summary.value.fileUri, props.tenant)
        : "",
);

const toRows = (entries: [string, string | undefined][]) =>
    entries.flatMap(([label, value]) => (value ? [{ label, value }] : []));

const requestRows = computed(() => {
    const request = summary.value?.request;
    if (!request) return [];
    const yesNo = (value: boolean | undefined) =>
        value === undefined ? undefined : t(value ? "yes" : "no");
    return toRows([
        [t("method"), request.method],
        [t("uri"), request.uri],
        [t("host"), request.host],
        [t("path"), request.path],
        [t("queryParams"), request.queryParamNames.join(", ")],
        [t("headerNames"), request.headerNames.join(", ")],
        [t("formFields"), request.formFieldNames.join(", ")],
        [t("contentType"), request.contentType],
        [
            t("bodySize"),
            request.hasBody && request.bodyBytes !== undefined
                ? formatBytes(request.bodyBytes)
                : undefined,
        ],
        [t("auth"), request.auth],
        [t("timeout"), request.timeout ? formatTimeout(request.timeout) : undefined],
        [t("followRedirects"), yesNo(request.followRedirects)],
    ]);
});

const responseRows = computed(() => {
    const value = summary.value;
    if (!value) return [];
    return toRows([
        [t("status"), formatStatus(value.code, value.reason)],
        [t("contentType"), value.contentType],
        [t("size"), value.bytes === undefined ? undefined : formatBytes(value.bytes)],
        [
            t("duration"),
            value.durationMs === undefined ? undefined : formatMillis(value.durationMs),
        ],
        [t("finalUri"), value.finalUri],
        [t("protocol"), value.protocol],
        [t("retries"), value.retries === undefined ? undefined : String(value.retries)],
    ]);
});
</script>

<template>
    <div class="http-drawer" data-test="http-summary-drawer">
        <KsSkeleton
            v-if="loading && !summary"
            :rows="6"
            animated
            data-test="http-summary-loading"
        />
        <KsAlert
            v-else-if="failed"
            type="error"
            :title="t('loadError')"
            :closable="false"
            data-test="http-summary-error"
        >
            <KsButton size="small" @click="reload">{{ t("retry") }}</KsButton>
        </KsAlert>
        <KsEmpty
            v-else-if="!summary"
            :description="t('noOutputs')"
            data-test="http-summary-empty"
        />
        <template v-else>
            <section class="section" data-test="http-summary-request">
                <KsText tag="h3" class="section-title">{{ t("request") }}</KsText>
                <KsAlert
                    v-if="!summary.request"
                    type="info"
                    :title="t('noRequest')"
                    :closable="false"
                    data-test="http-summary-no-request"
                />
                <KsTable v-else :data="requestRows" :showHeader="false" tableLayout="auto">
                    <KsTableColumn prop="label" />
                    <KsTableColumn prop="value" />
                </KsTable>
            </section>

            <section class="section" data-test="http-summary-response">
                <KsText tag="h3" class="section-title">{{ t("response") }}</KsText>
                <KsTable :data="responseRows" :showHeader="false" tableLayout="auto">
                    <KsTableColumn prop="label" />
                    <KsTableColumn prop="value" />
                </KsTable>
                <div v-if="summary.fileUri" class="file" data-test="http-summary-file">
                    <KsLink :href="fileUrl" target="_blank">
                        <KsTag>{{ downloadFileName(summary) }}</KsTag>
                    </KsLink>
                </div>
            </section>

            <section v-if="summary.headers.length" class="section" data-test="http-summary-headers">
                <KsText tag="h3" class="section-title">{{ t("responseHeaders") }}</KsText>
                <KsTable :data="summary.headers" tableLayout="auto">
                    <KsTableColumn prop="name" :label="t('name')" />
                    <KsTableColumn :label="t('value')">
                        <template #default="{ row }">
                            <code>{{ row.value }}</code>
                            <KsTag v-if="row.redacted" size="small" class="redacted">{{
                                t("redacted")
                            }}</KsTag>
                        </template>
                    </KsTableColumn>
                </KsTable>
            </section>

            <section
                v-if="summary.redirects.length"
                class="section"
                data-test="http-summary-redirects"
            >
                <KsText tag="h3" class="section-title">{{ t("redirects") }}</KsText>
                <KsTable :data="summary.redirects" tableLayout="auto">
                    <KsTableColumn prop="code" :label="t('status')" width="100" />
                    <KsTableColumn prop="location" :label="t('location')" />
                </KsTable>
            </section>

            <section v-if="summary.body" class="section" data-test="http-summary-body">
                <KsText tag="h3" class="section-title">{{ t("body") }}</KsText>
                <KsText
                    v-if="summary.body.kind === 'encrypted'"
                    data-test="http-summary-body-encrypted"
                >
                    {{ t("encrypted") }}
                </KsText>
                <template v-else>
                    <pre class="body" data-test="http-summary-body-text">{{
                        summary.body.text
                    }}</pre>
                    <KsText
                        v-if="summary.body.truncated"
                        size="small"
                        type="info"
                        data-test="http-summary-body-truncated"
                    >
                        {{ t("bodyTruncated", { count: BODY_PREVIEW_LIMIT }) }}
                    </KsText>
                </template>
            </section>
        </template>
    </div>
</template>

<style scoped>
.http-drawer {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-5);
}

.section-title {
    display: block;
    margin-bottom: var(--ks-spacing-2);
    font-weight: var(--ks-font-weight-semibold);
    color: var(--ks-text-primary);
}

.file {
    margin-top: var(--ks-spacing-3);
}

.redacted {
    margin-left: var(--ks-spacing-2);
}

.body {
    margin: 0;
    padding: var(--ks-spacing-3);
    max-height: 24rem;
    overflow: auto;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    background: var(--ks-bg-input);
    border: 1px solid var(--ks-border-default);
    color: var(--ks-text-primary);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
}
</style>