<template>
    <div class="loop-merged-logs" data-test="loop-merged-logs">
        <KsAlert
            v-if="failure && !lines.length"
            type="error"
            :title="failureMessage"
            data-test="loop-merged-logs-error"
        >
            <KsButton link type="danger" size="small" data-test="loop-merged-logs-retry" @click="refresh()">
                {{ $t("topology-graph.loop.retry") }}
            </KsButton>
        </KsAlert>

        <template v-else>
            <div v-if="scopeChain.length" class="loop-merged-scope" data-test="loop-merged-scope">
                <KsText size="small">{{ $t("topology-graph.loop.scope") }}</KsText>
                <KsTag size="small">{{ scopeLabel(t, scopeChain) }}</KsTag>
                <KsButton link size="small" data-test="loop-merged-scope-clear" @click="setEntries([])">
                    {{ $t("topology-graph.loop.clear-scopes") }}
                </KsButton>
            </div>

            <KsAlert
                v-if="targets?.truncated"
                type="info"
                :closable="false"
                :title="$t('logs_view.loop.truncated', {count: targets.failedShown}, targets.failedShown)"
                data-test="loop-merged-logs-truncated"
            />
            <KsAlert
                v-if="failure"
                type="error"
                :closable="false"
                :title="failureMessage"
                data-test="loop-merged-logs-stale"
            />

            <KsSkeleton v-if="!loaded || (loading && !lines.length)" :rows="4" animated data-test="loop-merged-logs-loading" />
            <KsNoData
                v-else-if="!rows.length"
                :title="$t('logs_view.loop.empty-title')"
                :description="$t('logs_view.loop.empty-description')"
                data-test="loop-merged-logs-empty"
            />
            <template v-else>
                <DynamicScroller
                    :items="rows"
                    :minItemSize="50"
                    keyField="uid"
                    class="loop-merged-scroller"
                    :class="{'loop-merged-scroller-full': fullHeight}"
                    data-test="loop-merged-logs-scroller"
                    :buffer="200"
                    :prerender="20"
                >
                    <template #default="{item, active}">
                        <DynamicScrollerItem :item="item" :active="active" :data-index="asRow(item).index">
                            <div class="loop-merged-row" data-test="loop-merged-row">
                                <div class="loop-merged-row-scope">
                                    <button
                                        type="button"
                                        class="loop-merged-scope-button"
                                        data-test="loop-merged-scope-button"
                                        :title="$t('logs_view.loop.scope-to', {scope: asRow(item).label})"
                                        @click="scopeTo(asRow(item).chain)"
                                    >
                                        {{ asRow(item).label }}
                                    </button>
                                    <router-link
                                        v-if="asRow(item).chain.length"
                                        class="loop-merged-overview-link"
                                        data-test="loop-merged-overview-link"
                                        :aria-label="$t('logs_view.loop.show-in-overview-for', {scope: asRow(item).label})"
                                        :to="overviewLocation(asRow(item).chain)"
                                    >
                                        {{ $t("logs_view.loop.show-in-overview") }}
                                    </router-link>
                                </div>
                                <LogLine
                                    class="loop-merged-line"
                                    :excludeMetas="EXCLUDED_METAS"
                                    :filter="filter"
                                    :log="asRow(item).log"
                                />
                            </div>
                        </DynamicScrollerItem>
                    </template>
                </DynamicScroller>
                <div v-if="hasMore" class="loop-merged-more">
                    <KsButton :loading="loadingMore" data-test="loop-merged-logs-more" @click="loadMore()">
                        {{ $t("topology-graph.loop.load-more") }}
                    </KsButton>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute, type RouteLocationRaw} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import LogLine from "../logs/LogLine.vue"
    import {useExecutionsStore} from "../../stores/executions"
    import {useLoopScope} from "../../composables/useLoopScope"
    import {useLoopMergedLogs} from "../../composables/useLoopMergedLogs"
    import {LOOP_SCOPE_QUERY_KEY, serializeLoopScope} from "../../utils/loopScope"
    import {scopeEntriesOf, scopeLabel, type ScopeNode} from "../../utils/loopLogScope"
    import {EXECUTION_PARENT_ROUTE} from "./executionTabs"
    import type {Log} from "../../stores/logs"

    interface MergedRow {
        index: number;
        uid: string;
        label: string;
        chain: ScopeNode[];
        log: Partial<Log>;
    }

    const EXCLUDED_METAS: (keyof Log)[] = ["namespace", "flowId", "executionId"]

    const props = defineProps<{
        levelParams: Record<string, string>;
        running: boolean;
        filter?: string;
        fullHeight?: boolean;
    }>()

    const {t} = useI18n()
    const route = useRoute()
    const executionsStore = useExecutionsStore()
    const {entries, setEntries} = useLoopScope()

    const root = computed(() => {
        const execution = executionsStore.execution
        if (!execution?.id) return undefined
        return {
            id: execution.id,
            namespace: execution.namespace,
            flowId: execution.flowId,
            startDate: execution.state?.startDate,
            endDate: props.running ? undefined : execution.state?.endDate ?? undefined,
            kind: (execution as {kind?: string}).kind,
        }
    })

    const {targets, lines, loading, loadingMore, loaded, failure, hasMore, refresh, loadMore} = useLoopMergedLogs({
        root,
        scope: entries,
        levelParams: computed(() => props.levelParams),
        running: computed(() => props.running),
        q: computed(() => props.filter || undefined),
    })

    const scopeChain = computed(() => targets.value?.scope ?? [])

    const failureMessage = computed(() => t(`topology-graph.loop.failure-${failure.value ?? "unknown"}`))

    const rows = computed<MergedRow[]>(() => lines.value.map((line, index) => {
        const chain = (line.executionId && targets.value?.chains[line.executionId]) || []
        return {
            index,
            uid: `${line.executionId ?? ""}-${line.taskRunId ?? ""}-${line.attemptNumber ?? 0}-${line.timestamp ?? ""}-${index}`,
            label: chain.length ? scopeLabel(t, chain) : t("logs_view.loop.this-execution"),
            chain,
            log: line as Partial<Log>,
        }
    }))

    const asRow = (item: unknown) => item as MergedRow

    const scopeTo = (chain: ScopeNode[]) => setEntries(scopeEntriesOf(chain))

    const overviewLocation = (chain: ScopeNode[]): RouteLocationRaw => ({
        name: `${EXECUTION_PARENT_ROUTE}/overview`,
        params: route.params,
        query: {[LOOP_SCOPE_QUERY_KEY]: serializeLoopScope(scopeEntriesOf(chain))},
    })

    defineExpose({refresh})
</script>

<style scoped lang="scss">
    .loop-merged-logs {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        min-height: 0;
        flex: 1;
    }

    .loop-merged-scope {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .loop-merged-scroller {
        max-height: calc(100vh - 335px);
        background-color: var(--ks-bg-surface);
        border-radius: var(--ks-spacing-1);
    }

    .loop-merged-scroller-full {
        flex: 1;
        max-height: none;
        min-height: 0;
    }

    .loop-merged-row {
        display: grid;
        grid-template-columns: minmax(10rem, 16rem) 1fr;
        gap: var(--ks-spacing-2);
        align-items: start;
        border-bottom: 1px solid var(--ks-border-default);
    }

    .loop-merged-row-scope {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        align-items: flex-start;
        padding: var(--ks-spacing-2);
        min-width: 0;
    }

    .loop-merged-scope-button {
        max-width: 100%;
        padding: 0;
        border: 0;
        background: none;
        color: var(--ks-text-link);
        font-size: var(--ks-font-size-xs);
        text-align: left;
        overflow-wrap: anywhere;
        cursor: pointer;
    }

    .loop-merged-overview-link {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .loop-merged-line {
        min-width: 0;
        padding: var(--ks-spacing-2);
    }

    .loop-merged-more {
        display: flex;
        justify-content: center;
        padding: var(--ks-spacing-2) 0;
    }
</style>
