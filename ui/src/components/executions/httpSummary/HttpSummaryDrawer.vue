<template>
    <div class="http-drawer" data-test="http-summary-drawer">
        <KsSkeleton v-if="loading && !summary" :rows="6" animated data-test="http-summary-loading" />
        <KsAlert
            v-else-if="failed"
            type="error"
            :title="$t('http_summary.load_error')"
            :closable="false"
            data-test="http-summary-error"
        >
            <KsButton size="small" @click="reload">{{ $t("http_summary.retry") }}</KsButton>
        </KsAlert>
        <KsEmpty v-else-if="!summary" :description="$t('http_summary.no_outputs')" data-test="http-summary-empty" />
        <template v-else>
            <section class="section" data-test="http-summary-request">
                <KsText tag="h3" class="section-title">{{ $t("http_summary.request") }}</KsText>
                <KsAlert
                    v-if="!summary.request"
                    type="info"
                    :title="$t('http_summary.no_request')"
                    :closable="false"
                    data-test="http-summary-no-request"
                />
                <KsTable v-else :data="requestRows" :showHeader="false" tableLayout="auto">
                    <KsTableColumn prop="label" />
                    <KsTableColumn prop="value" />
                </KsTable>
            </section>

            <section class="section" data-test="http-summary-response">
                <KsText tag="h3" class="section-title">{{ $t("http_summary.response") }}</KsText>
                <KsTable :data="responseRows" :showHeader="false" tableLayout="auto">
                    <KsTableColumn prop="label" />
                    <KsTableColumn prop="value" />
                </KsTable>
                <div v-if="summary.fileUri" class="file" data-test="http-summary-file">
                    <KsLink :href="fileUrl" target="_blank">
                        <KsFileTag :uri="summary.fileUri" />
                    </KsLink>
                </div>
            </section>

            <section v-if="summary.headers.length" class="section" data-test="http-summary-headers">
                <KsText tag="h3" class="section-title">{{ $t("http_summary.response_headers") }}</KsText>
                <KsTable :data="summary.headers" tableLayout="auto">
                    <KsTableColumn prop="name" :label="$t('http_summary.name')" />
                    <KsTableColumn :label="$t('http_summary.value')">
                        <template #default="{row}">
                            <code>{{ row.value }}</code>
                            <KsTag v-if="row.redacted" size="small" class="redacted">{{ $t("http_summary.redacted") }}</KsTag>
                        </template>
                    </KsTableColumn>
                </KsTable>
            </section>

            <section v-if="summary.redirects.length" class="section" data-test="http-summary-redirects">
                <KsText tag="h3" class="section-title">{{ $t("http_summary.redirects") }}</KsText>
                <KsTable :data="summary.redirects" tableLayout="auto">
                    <KsTableColumn prop="code" :label="$t('http_summary.status')" width="100" />
                    <KsTableColumn prop="location" :label="$t('http_summary.location')" />
                </KsTable>
            </section>

            <section v-if="summary.body" class="section" data-test="http-summary-body">
                <KsText tag="h3" class="section-title">{{ $t("http_summary.body") }}</KsText>
                <KsText v-if="summary.body.kind === 'encrypted'" data-test="http-summary-body-encrypted">
                    {{ $t("http_summary.encrypted") }}
                </KsText>
                <template v-else>
                    <pre class="body" data-test="http-summary-body-text">{{ summary.body.text }}</pre>
                    <KsText v-if="summary.body.truncated" size="small" type="info" data-test="http-summary-body-truncated">
                        {{ $t("http_summary.body_truncated", {count: BODY_PREVIEW_LIMIT}) }}
                    </KsText>
                </template>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {
        KsAlert, KsButton, KsEmpty, KsFileTag, KsLink, KsSkeleton, KsTable, KsTableColumn, KsTag, KsText,
    } from "@kestra-io/design-system"
    import type {Execution, Task} from "@kestra-io/kestra-sdk"
    import {humanFileSize} from "../../../utils/utils"
    import {executionFileUrl} from "../../../utils/executionUtils"
    import {BODY_PREVIEW_LIMIT, formatMillis, formatTimeout, httpTaskKind} from "./httpSummary"
    import {taskRunRefreshKey, useHttpSummary, type FetchOutputs} from "./useHttpSummary"

    const props = defineProps<{
        task: Task
        execution?: Execution
        fetchOutputs?: FetchOutputs
    }>()

    const {t} = useI18n()

    const {summary, loading, failed, reload} = useHttpSummary(
        () => props.fetchOutputs,
        () => httpTaskKind(props.task.type),
        () => taskRunRefreshKey(props.execution, props.task.id),
    )

    const fileUrl = computed(() => summary.value?.fileUri ? executionFileUrl(props.execution?.id, summary.value.fileUri) : "")

    const toRows = (entries: [string, string | undefined][]) =>
        entries.flatMap(([label, value]) => value ? [{label, value}] : [])

    const requestRows = computed(() => {
        const request = summary.value?.request
        if (!request) return []
        const yesNo = (value: boolean | undefined) => value === undefined ? undefined : t(value ? "yes" : "no")
        return toRows([
            [t("http_summary.method"), request.method],
            [t("http_summary.uri"), request.uri],
            [t("http_summary.host"), request.host],
            [t("http_summary.path"), request.path],
            [t("http_summary.query_params"), request.queryParamNames.join(", ")],
            [t("http_summary.header_names"), request.headerNames.join(", ")],
            [t("http_summary.form_fields"), request.formFieldNames.join(", ")],
            [t("http_summary.content_type"), request.contentType],
            [t("http_summary.body_size"), request.hasBody && request.bodyBytes !== undefined ? humanFileSize(request.bodyBytes) : undefined],
            [t("http_summary.auth"), request.auth],
            [t("http_summary.timeout"), request.timeout ? formatTimeout(request.timeout) : undefined],
            [t("http_summary.follow_redirects"), yesNo(request.followRedirects)],
        ])
    })

    const responseRows = computed(() => {
        const value = summary.value
        if (!value) return []
        return toRows([
            [t("http_summary.status"), value.code === undefined ? undefined : [value.code, value.reason].filter(Boolean).join(" ")],
            [t("http_summary.content_type"), value.contentType],
            [t("http_summary.size"), value.bytes === undefined ? undefined : humanFileSize(value.bytes)],
            [t("http_summary.duration"), value.durationMs === undefined ? undefined : formatMillis(value.durationMs)],
            [t("http_summary.final_uri"), value.finalUri],
            [t("http_summary.protocol"), value.protocol],
            [t("http_summary.retries"), value.retries === undefined ? undefined : String(value.retries)],
        ])
    })
</script>

<style lang="scss" scoped>
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
