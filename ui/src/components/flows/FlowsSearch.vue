<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="full-container source-search">
        <div class="source-search__header">
            <div class="source-search__query-row">
                <KsIconButton
                    :aria-expanded="replaceOpen"
                    :aria-pressed="replaceOpen"
                    :aria-label="replaceOpen ? t('source_search.hide_replace') : t('source_search.show_replace')"
                    :tooltip="replaceOpen ? t('source_search.hide_replace') : t('source_search.show_replace')"
                    class="source-search__replace-toggle"
                    :class="{'source-search__replace-toggle--active': replaceOpen}"
                    @click="replaceOpen = !replaceOpen"
                >
                    <FindReplace />
                </KsIconButton>

                <div class="source_search__input-stack">
                    <div class="source-search__search-row">
                        <KsSearch
                            v-model="query"
                            clearable
                            :placeholder="t('source_search.search_placeholder')"
                            :aria-label="t('source_search.search_aria')"
                            :aria-invalid="Boolean(errorMessage)"
                            @keydown.down.prevent="goToMatch(1)"
                            @keydown.up.prevent="goToMatch(-1)"
                            @keydown.enter.exact.prevent="goToMatch(1)"
                            @keydown.enter.shift.prevent="goToMatch(-1)"
                        />
                        <div class="source-search__toggles" role="group" :aria-label="t('source_search.options_aria')">
                            <KsCheckboxButton
                                v-model="caseSensitive"
                                size="small"
                                :title="t('source_search.match_case')"
                                :aria-label="t('source_search.match_case')"
                            >
                                <span class="source-search__toggle-label">Aa</span>
                            </KsCheckboxButton>
                            <KsCheckboxButton
                                v-model="wholeWord"
                                size="small"
                                :title="t('source_search.match_whole_word')"
                                :aria-label="t('source_search.match_whole_word')"
                            >
                                <span class="source-search__toggle-label"><u>ab</u></span>
                            </KsCheckboxButton>
                            <KsCheckboxButton
                                v-model="regexEnabled"
                                size="small"
                                :title="t('source_search.use_regex')"
                                :aria-label="t('source_search.use_regex')"
                            >
                                <span class="source-search__toggle-label">.*</span>
                            </KsCheckboxButton>
                        </div>
                    </div>

                    <div v-if="replaceOpen" class="source-search__replace-row">
                        <KsSearch
                            v-model="replacement"
                            :placeholder="t('source_search.replace_placeholder')"
                            :aria-label="t('source_search.replace_aria')"
                        >
                            <template #prefix>
                                <FindReplace />
                            </template>
                        </KsSearch>
                        <KsButton
                            :type="showDiffPreview ? 'primary' : 'default'"
                            :disabled="!query"
                            :loading="previewLoading"
                            :tooltip="t('source_search.replace_all_tooltip')"
                            @click="triggerReplacePreview"
                        >
                            {{ t('source_search.replace_all') }}
                        </KsButton>
                    </div>
                </div>
            </div>

            <div v-if="query" class="source-search__scope-row">
                <div class="source-search__field">
                    <label>{{ t('namespace') }}</label>
                    <NamespaceSelect
                        v-model="namespace"
                        data-type="flow"
                        @update:model-value="onNamespaceChange"
                    />
                </div>
                <div class="source-search__field">
                    <label>{{ t('source_search.scope') }}</label>
                    <KsSegmented v-model="scope" size="small" :options="scopeOptions" />
                </div>

                <div class="source-search__spacer" />

                <i18n-t
                    v-if="hasResults"
                    keypath="source_search.summary"
                    tag="span"
                    class="source-search__summary"
                >
                    <template #matches>
                        <strong>{{ totalMatchCount }}</strong>
                    </template>
                    <template #flows>
                        <strong>{{ results.length }}</strong>
                    </template>
                </i18n-t>

                <div v-if="hasResults" class="source-search__match-nav">
                    <KsIconButton
                        :disabled="flatMatches.length === 0"
                        :tooltip="t('source_search.previous_match')"
                        @click="goToMatch(-1)"
                    >
                        <ChevronUp />
                    </KsIconButton>
                    <span class="source-search__match-count">{{ matchNavLabel }}</span>
                    <KsIconButton
                        :disabled="flatMatches.length === 0"
                        :tooltip="t('source_search.next_match')"
                        @click="goToMatch(1)"
                    >
                        <ChevronDown />
                    </KsIconButton>
                    <KsIconButton
                        :tooltip="allCollapsed ? t('source_search.expand_all') : t('source_search.collapse_all')"
                        @click="toggleCollapseAll"
                    >
                        <ArrowExpandVertical v-if="allCollapsed" />
                        <ArrowCollapseVertical v-else />
                    </KsIconButton>
                </div>
            </div>
        </div>

        <KsAlert
            v-if="showDiffPreview && readOnlyExcludedCount > 0"
            type="warning"
            class="source-search__rbac-banner"
        >
            <i18n-t keypath="source_search.rbac_banner" tag="span">
                <template #count>
                    <b>{{ readOnlyExcludedCount }}</b>
                </template>
                <template #namespace>
                    <code>{{ firstReadOnlyNamespace }}</code>
                </template>
                <template #permission>
                    <b>{{ t('source_search.flow_update_permission') }}</b>
                </template>
            </i18n-t>
        </KsAlert>

        <div v-if="loading" class="source-search__states">
            <div class="source-search__skeleton-rows">
                <KsSkeleton v-for="n in 4" :key="n" animated :rows="1" class="source-search__skeleton-row" />
            </div>
        </div>

        <div v-else-if="!query" class="source-search__states">
            <KsEmpty :background="false">
                <template #image>
                    <span class="source-search__empty-glyph">
                        <Magnify />
                    </span>
                </template>
                <template #description>
                    <h3>{{ t('source_search.empty_title') }}</h3>
                    <p>{{ t('source_search.empty_description') }}</p>
                </template>
                <div class="source-search__examples" role="list" :aria-label="t('source_search.examples_aria')">
                    <button
                        v-for="example in exampleQueries"
                        :key="example"
                        type="button"
                        class="source-search__example-chip"
                        role="listitem"
                        @click="query = example"
                    >
                        {{ example }}
                    </button>
                </div>
            </KsEmpty>
        </div>

        <div v-else-if="errorMessage" class="source-search__states">
            <KsAlert type="error" :title="t('source_search.error_title')" :description="errorMessage" />
            <KsButton type="default" @click="fetchResults">
                {{ t('source_search.retry_search') }}
            </KsButton>
        </div>

        <div v-else-if="!hasResults" class="source-search__states">
            <KsEmpty :background="false">
                <template #description>
                    <h3>{{ t('source_search.no_results_title', {query}) }}</h3>
                    <p>{{ t('source_search.no_results_description') }}</p>
                </template>
            </KsEmpty>
        </div>

        <KsSplitter v-else class="source-search__splitter">
            <KsSplitterPanel min="20%" size="38%" key="results">
                <SourceSearchResults
                    ref="resultsRef"
                    :results="results"
                    :selectedKey="selectedKey"
                    :replaceMode="replaceOpen"
                    :selectedMatchKeys="selectedMatchKeys"
                    :replaceContext="replaceContext"
                    data-test="source-search-results-pane"
                    @select="onSelect"
                    @toggle-flow="onToggleFlow"
                    @toggle-match="onToggleMatch"
                    @replace-flow="onReplaceFlow"
                    @replace-match="onReplaceMatch"
                />
            </KsSplitterPanel>
            <KsSplitterPanel min="20%" key="preview">
                <SourceSearchPreview
                    :selected="selected"
                    :query="query"
                    :replaceMode="showDiffPreview"
                    :previewResponse="previewResponse"
                    :selectionSummary="showDiffPreview ? selectionSummary : null"
                    :readOnlyExcludedCount="readOnlyExcludedCount"
                    data-test="source-search-preview-pane"
                    @cancel="previewResponse = null"
                    @replace-all="onConfirmReplaceAll"
                />
            </KsSplitterPanel>
        </KsSplitter>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import debounce from "lodash/debounce"
    import TopNavBar from "../layout/TopNavBar.vue"
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue"
    import SourceSearchResults from "./SourceSearchResults.vue"
    import SourceSearchPreview from "./SourceSearchPreview.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ArrowCollapseVertical from "vue-material-design-icons/ArrowCollapseVertical.vue"
    import ArrowExpandVertical from "vue-material-design-icons/ArrowExpandVertical.vue"
    import FindReplace from "vue-material-design-icons/FindReplace.vue"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import useRestoreUrl from "../../composables/useRestoreUrl"
    import {useToast} from "../../utils/toast"
    import {computeSelectionSummary, distinctSkipReasons, type ReplaceContext, type SourceSearchResult} from "../../utils/sourceSearchDiff"

    import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
    import type {SourceSearchReplacePreviewResponse, SourceSearchReplaceApplyResponse, SourceSearchScope} from "@kestra-io/kestra-sdk"

    const {loadInit} = useRestoreUrl()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()

    const resultsRef = ref<InstanceType<typeof SourceSearchResults> | null>(null)

    const loading = ref(false)
    const errorMessage = ref<string | null>(null)
    const selected = ref<{namespace: string; id: string; line: number; column: number} | null>(null)
    const selectedMatchKeys = ref<Set<string>>(new Set())
    const previewResponse = ref<SourceSearchReplacePreviewResponse | null>(null)
    const previewLoading = ref(false)
    const allCollapsed = ref(false)

    const replaceOpen = ref(false)
    const replacement = ref("")
    const caseSensitive = ref(false)
    const wholeWord = ref(false)
    const regexEnabled = ref(false)

    const exampleQueries = [
        "io.kestra.plugin.gcp.bigquery.Query",
        "retry:",
        "concurrency:\\s*\\n\\s*limit:",
        "secret('...')",
    ]

    const scopeOptions = computed(() => [
        {label: t("source_search.scope_all"), value: "all"},
        {label: t("source_search.scope_tasks"), value: "tasks"},
        {label: t("source_search.scope_triggers"), value: "triggers"},
        {label: t("source_search.scope_inputs"), value: "inputs"},
    ])

    const routeInfo = computed(() => ({
        title: (route.meta?.title as string) ?? t("source search"),
        breadcrumb: [
            {
                label: t("flows"),
                link: {name: "flows/list"},
            },
        ],
    }))

    useRouteContext(routeInfo)

    function pushQuery(mutate: (query: Record<string, any>) => void, options?: {replace?: boolean}) {
        const routeQuery = {...route.query}
        mutate(routeQuery)
        if (options?.replace) router.replace({query: routeQuery})
        else router.push({query: routeQuery})
    }

    const query = computed({
        get: () => (route.query.q as string) ?? "",
        set: (value: string) => pushQuery((q) => {
            if (value) q.q = value
            else delete q.q
        }, {replace: true}),
    })

    const namespace = computed({
        get: () => route.query?.namespace as [],
        set: (val) => onNamespaceChange(val),
    })

    const namespaceFilter = computed<string | undefined>(() => {
        const raw = route.query?.namespace
        if (Array.isArray(raw)) return raw[0] as string
        return typeof raw === "string" && raw ? raw : undefined
    })

    const scope = computed({
        get: () => (route.query.scope as string) ?? "all",
        set: (value: string) => pushQuery((q) => {
            q.scope = value
        }),
    })

    function onNamespaceChange(val: any) {
        pushQuery((q) => {
            if (val === undefined || val === "" || val === null || (Array.isArray(val) && val.length === 0)) {
                delete q.namespace
            } else {
                q.namespace = val
            }
        })
    }

    const results = ref<SourceSearchResult[]>([])
    const hasResults = computed(() => results.value.length > 0)
    const totalMatchCount = computed(() => results.value.reduce((sum, group) => sum + group.matches.length, 0))
    const readOnlyExcludedCount = computed(() => results.value.filter((group) => !group.editable).length)
    const firstReadOnlyNamespace = computed(() => results.value.find((group) => !group.editable)?.namespace ?? "")

    const selectedKey = computed(() => selected.value ? `${selected.value.namespace}.${selected.value.id}#${selected.value.line}:${selected.value.column}` : null)

    const showDiffPreview = computed(() => previewResponse.value !== null)

    const selectionSummary = computed(() => computeSelectionSummary(results.value, selectedMatchKeys.value))

    const flatMatches = computed(() => {
        const list: {namespace: string; id: string; line: number; column: number}[] = []
        for (const group of results.value) {
            for (const match of group.matches) {
                list.push({namespace: group.namespace, id: group.id, line: match.line, column: match.column})
            }
        }
        return list
    })

    const activeMatchIndex = computed(() => {
        if (!selected.value) return -1
        return flatMatches.value.findIndex((match) => match.namespace === selected.value!.namespace && match.id === selected.value!.id && match.line === selected.value!.line && match.column === selected.value!.column)
    })

    const matchNavLabel = computed(() => {
        if (flatMatches.value.length === 0) return t("source_search.match_nav_empty")
        return t("source_search.match_nav", {current: activeMatchIndex.value + 1, total: flatMatches.value.length})
    })

    function matchKey(matchNamespace: string, id: string, line: number, column: number) {
        return `${matchNamespace}.${id}#${line}:${column}`
    }

    function onSelect(value: {namespace: string; id: string; line: number; column: number}) {
        selected.value = value
    }

    function goToMatch(delta: number) {
        if (flatMatches.value.length === 0) return
        const nextIndex = (activeMatchIndex.value + delta + flatMatches.value.length) % flatMatches.value.length
        selected.value = {...flatMatches.value[nextIndex]}
    }

    function toggleCollapseAll() {
        allCollapsed.value = !allCollapsed.value
        if (allCollapsed.value) {
            resultsRef.value?.collapseAll()
        } else {
            resultsRef.value?.expandAll()
        }
    }

    function onToggleFlow(value: {namespace: string; id: string; checked: boolean}) {
        const group = results.value.find((g) => g.namespace === value.namespace && g.id === value.id)
        if (!group) return
        const next = new Set(selectedMatchKeys.value)
        for (const match of group.matches) {
            const key = matchKey(group.namespace, group.id, match.line, match.column)
            if (value.checked) next.add(key)
            else next.delete(key)
        }
        selectedMatchKeys.value = next
    }

    function onToggleMatch(value: {namespace: string; id: string; line: number; column: number; checked: boolean}) {
        const next = new Set(selectedMatchKeys.value)
        const key = matchKey(value.namespace, value.id, value.line, value.column)
        if (value.checked) next.add(key)
        else next.delete(key)
        selectedMatchKeys.value = next
    }

    function onReplaceFlow(value: {namespace: string; id: string}) {
        return applyReplace([{namespace: value.namespace, id: value.id}])
    }

    const searchFilters = computed(() => ({
        caseSensitive: caseSensitive.value,
        wholeWord: wholeWord.value,
        regex: regexEnabled.value,
        scope: scope.value.toUpperCase() as SourceSearchScope,
    }))

    const replaceContext = computed<ReplaceContext | null>(() => (replaceOpen.value && replacement.value)
        ? {
            query: query.value,
            replacement: replacement.value,
            regex: regexEnabled.value,
            caseSensitive: caseSensitive.value,
            wholeWord: wholeWord.value,
        }
        : null)

    async function triggerReplacePreview() {
        if (!query.value) return
        previewLoading.value = true
        try {
            previewResponse.value = await FlowsAPI.previewReplaceBySourceCode({
                ...searchFilters.value,
                query: query.value,
                namespace: namespaceFilter.value,
                replacement: replacement.value,
            })
        } catch (e: any) {
            toast.error(e?.response?.data?.message ?? t("source_search.replace_preview_failed"))
        } finally {
            previewLoading.value = false
        }
    }

    function reportReplaceResult(response: SourceSearchReplaceApplyResponse) {
        if (response.updated?.length) {
            toast.success(t("source_search.replace_apply_success", {count: response.updated.length}))
        }
        if (response.skipped?.length) {
            const reasons = distinctSkipReasons(response.skipped)
                .map((reason) => t(`source_search.replace_skip_reason.${reason}`))
                .join(", ")
            toast.warning(t("source_search.replace_apply_skipped", {count: response.skipped.length, reasons}))
        }
        previewResponse.value = null
        return fetchResults()
    }

    async function applyReplace(flows: {namespace: string; id: string}[]) {
        if (flows.length === 0 || !query.value) return
        try {
            await reportReplaceResult(await FlowsAPI.applyReplaceBySourceCode({
                ...searchFilters.value,
                query: query.value,
                replacement: replacement.value,
                flows,
            }))
        } catch (e: any) {
            toast.error(e?.response?.data?.message ?? t("source_search.replace_apply_failed"))
        }
    }

    async function onReplaceMatch(value: {namespace: string; id: string; line: number; column: number}) {
        if (!query.value) return
        try {
            await reportReplaceResult(await FlowsAPI.replaceLineBySourceCode({
                query: query.value,
                caseSensitive: caseSensitive.value,
                wholeWord: wholeWord.value,
                regex: regexEnabled.value,
                replacement: replacement.value,
                namespace: value.namespace,
                id: value.id,
                line: value.line,
                column: value.column,
            }))
        } catch (e: any) {
            toast.error(e?.response?.data?.message ?? t("source_search.replace_apply_failed"))
        }
    }

    function onConfirmReplaceAll() {
        const flowsToApply = results.value
            .filter((group) => group.editable && group.matches.some((match) => selectedMatchKeys.value.has(matchKey(group.namespace, group.id, match.line, match.column))))
            .map((group) => ({namespace: group.namespace, id: group.id}))
        return applyReplace(flowsToApply)
    }

    async function fetchResults() {
        if (!loadInit.value || !query.value) {
            if (!query.value) {
                results.value = []
            }
            return
        }

        loading.value = true
        errorMessage.value = null
        previewResponse.value = null

        try {
            const response = await FlowsAPI.searchFlowsBySourceCode({
                ...searchFilters.value,
                page: 1,
                size: 200,
                q: query.value,
                namespace: namespaceFilter.value,
            })
            results.value = response.results as SourceSearchResult[]
        } catch (e: any) {
            errorMessage.value = e?.response?.data?.message ?? t("source_search.search_failed")
            results.value = []
        } finally {
            loading.value = false
        }
    }

    const debouncedFetch = debounce(fetchResults, 300)

    watch(
        () => [query.value, namespace.value, JSON.stringify(searchFilters.value)].join("|"),
        () => {
            loading.value = Boolean(loadInit.value && query.value)
            debouncedFetch()
        },
    )

    watch(results, (newResults) => {
        selectedMatchKeys.value = new Set(
            newResults
                .filter((group) => group.editable)
                .flatMap((group) => group.matches.map((match) => matchKey(group.namespace, group.id, match.line, match.column))),
        )

        if (newResults.length > 0 && newResults[0].matches.length > 0) {
            const stillValid = selected.value && newResults.some((group) => group.namespace === selected.value!.namespace && group.id === selected.value!.id && group.matches.some((match) => match.line === selected.value!.line && match.column === selected.value!.column))
            if (!stillValid) {
                selected.value = {namespace: newResults[0].namespace, id: newResults[0].id, line: newResults[0].matches[0].line, column: newResults[0].matches[0].column}
            }
        } else {
            selected.value = null
        }
    })

    fetchResults()
</script>

<style scoped lang="scss">
.source-search {
    display: flex;
    flex-direction: column;
    min-height: 0;
    flex: 1;
}

.source-search__header {
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
    background: var(--ks-bg-surface);
    flex: 0 0 auto;
}

.source-search__query-row {
    display: flex;
    align-items: stretch;
    gap: var(--ks-spacing-2);
}

.source-search__replace-toggle--active {
    color: var(--ks-text-link);
    background: var(--ks-bg-hover);
}

.source_search__input-stack {
    flex: 1 1 auto;
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
    min-width: 0;
}

.source-search__search-row,
.source-search__replace-row {
    display: flex;
    align-items: stretch;
    gap: var(--ks-spacing-2);
}

.source-search__toggles {
    display: flex;
    align-items: stretch;
    flex: 0 0 auto;
}

.source-search__toggle-label {
    display: inline-block;
    min-width: 2.75ch;
    text-align: center;
}

.source-search__scope-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-3);
    margin-top: var(--ks-spacing-3);
    padding-top: var(--ks-spacing-3);
    border-top: 1px solid var(--ks-border-subtle);
}

.source-search__field {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);

    label {
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }
}

.source-search__spacer {
    flex: 1 1 auto;
}

.source-search__summary {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-muted);
    white-space: nowrap;
}

.source-search__match-nav {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
}

.source-search__match-count {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);
    min-width: 8.5ch;
    text-align: center;
    font-variant-numeric: tabular-nums;
}

.source-search__rbac-banner {
    flex: 0 0 auto;
    border-radius: 0;
}

.source-search__states {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-8) var(--ks-spacing-5);
    text-align: center;

    h3 {
        margin: 0 0 var(--ks-spacing-2);
        font-size: var(--ks-font-size-lg);
    }

    p {
        margin: 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        max-width: 48ch;
    }
}

.source-search__skeleton-rows {
    width: 100%;
    max-width: 40rem;
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
}

.source-search__empty-glyph {
    width: 3rem;
    height: 3rem;
    border-radius: var(--ks-radius-lg);
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--ks-bg-base);
    border: 1px solid var(--ks-border-default);
    color: var(--ks-text-muted);
    margin: 0 auto var(--ks-spacing-3);
}

.source-search__examples {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
    justify-content: center;
    margin-top: var(--ks-spacing-2);
}

.source-search__example-chip {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    background: var(--ks-bg-base);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    padding: var(--ks-spacing-1) var(--ks-spacing-2);
    color: var(--ks-text-link);
    cursor: pointer;

    &:hover {
        border-color: var(--ks-border-strong);
    }
}

.source-search__splitter {
    flex: 1;
    min-height: 0;
    overflow: hidden;
}
</style>
