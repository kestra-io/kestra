<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="full-container flush-top source-search">
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
                            :aria-invalid="failedSelectedTypes.includes('flows')"
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

                    <p v-if="showFlowOnlyToggleHint" class="source-search__toggle-hint">
                        <InformationOutline />
                        {{ t('source_search.flow_only_toggle_hint') }}
                    </p>

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

                    <p v-if="replaceOpen" class="source-search__toggle-hint">
                        <InformationOutline />
                        {{ t('source_search.replace_scope_hint') }}
                    </p>

                    <KsAlert
                        v-if="crossResourceSearchStore.truncatedFor('flows')"
                        type="warning"
                        class="source-search__truncation-alert"
                        :description="t('source_search.truncated_flows', {shown: crossResourceSearchStore.countFor('flows'), total: crossResourceSearchStore.totalFor('flows')})"
                    />
                </div>
            </div>

            <div class="source-search__scope-row" data-test="source-search-type-pills">
                <span id="source-search-in-label" class="source-search__scope-label">{{ t('source_search.search_in') }}</span>
                <div class="source-search__pills" role="group" aria-labelledby="source-search-in-label">
                    <KsCheckTag
                        v-for="type in SEARCH_RESOURCE_TYPES"
                        :key="type"
                        pill
                        :checked="selectedTypes.includes(type)"
                        :class="{'source-search__pill--failed': query && crossResourceSearchStore.statusFor(type) === 'failed'}"
                        :title="type !== 'flows' ? t(type === 'files' ? 'source_search.path_only_hint' : 'source_search.key_only_hint') : undefined"
                        @change="(checked) => setTypeSelected(type, checked)"
                    >
                        <template #icon>
                            <component :is="typeIcon(type)" />
                        </template>
                        {{ typeLabel(type) }}
                        <span v-if="query && crossResourceSearchStore.statusFor(type) !== 'idle'" class="source-search__pill-count">{{ crossResourceSearchStore.countFor(type) }}</span>
                        <Loading v-if="crossResourceSearchStore.statusFor(type) === 'counting'" class="source-search__pill-spin" />
                        <AlertCircleOutline v-else-if="query && crossResourceSearchStore.statusFor(type) === 'failed'" :title="crossResourceSearchStore.errorMessageFor(type)" />
                        <PencilOff v-else-if="type !== 'flows'" />
                    </KsCheckTag>
                    <KsButton class="source-search__pill-outline" size="small" @click="selectAllTypes">
                        {{ t('source_search.select_all_types') }}
                    </KsButton>
                </div>
            </div>

            <div v-if="query" class="source-search__filter-row">
                <div class="source-search__field">
                    <label>{{ t('namespace') }}</label>
                    <NamespaceSelect
                        v-model="namespace"
                        data-type="flow"
                        @update:model-value="onNamespaceChange"
                    />
                </div>
                <div v-if="selectedTypes.includes('flows')" class="source-search__field">
                    <label>{{ t('source_search.flow_section') }}</label>
                    <KsSegmented v-model="scope" size="small" :options="scopeOptions" />
                </div>

                <div class="source-search__spacer" />

                <i18n-t
                    v-if="!showLoadingState"
                    keypath="source_search.summary_cross"
                    tag="span"
                    class="source-search__summary"
                >
                    <template #matches>
                        <strong>{{ t('source_search.match_count', summaryMatchCount) }}</strong>
                    </template>
                    <template #resources>
                        <strong>{{ t('source_search.count_resources', summaryResourceCount) }}</strong>
                    </template>
                    <template #types>
                        <strong>{{ t('source_search.count_types', summaryActiveTypeCount) }}</strong>
                    </template>
                </i18n-t>

                <div v-if="!showLoadingState" class="source-search__match-nav">
                    <KsIconButton
                        :disabled="visibleFlatSelections.length === 0"
                        :tooltip="t('source_search.previous_match')"
                        @click="goToMatch(-1)"
                    >
                        <ChevronUp />
                    </KsIconButton>
                    <span class="source-search__match-count">{{ matchNavLabel }}</span>
                    <KsIconButton
                        :disabled="visibleFlatSelections.length === 0"
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
            v-for="type in failedSelectedTypes"
            :key="`failed-${type}`"
            type="error"
            class="source-search__type-failed-banner"
        >
            <div class="source-search__alert-title">{{ t('source_search.type_search_failed', {type: typeLabel(type)}) }}</div>
            <div>{{ crossResourceSearchStore.errorMessageFor(type) }}</div>
        </KsAlert>

        <KsAlert
            v-if="showDiffPreview && totalExcludedFromReplaceCount > 0"
            type="warning"
            class="source-search__rbac-banner"
        >
            <div class="source-search__alert-title">
                {{ t('source_search.exclusion_title', {count: totalExcludedFromReplaceCount}) }}
            </div>
            <ul class="source-search__exclusion-list">
                <li v-if="flowsReadOnlyMatchCount > 0">
                    {{ t('source_search.exclusion_flows_readonly', {count: flowsReadOnlyMatchCount}) }}
                </li>
                <li v-for="item in nonFlowSearchOnlyExclusions" :key="item.type">
                    {{ t('source_search.exclusion_search_only', {type: typeLabel(item.type), count: item.count}) }}
                </li>
            </ul>
        </KsAlert>

        <KsAlert
            v-if="filesProgressInfo"
            type="info"
            class="source-search__progress-banner"
        >
            {{ t(filesProgressInfo.failed > 0 ? 'source_search.files_progress_banner_failed' : 'source_search.files_progress_banner_ok', filesProgressInfo) }}
        </KsAlert>

        <div v-if="!query" class="source-search__states">
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

        <div v-else-if="showLoadingState" class="source-search__states">
            <div class="source-search__skeleton-rows">
                <KsSkeleton v-for="n in 4" :key="n" animated :rows="1" class="source-search__skeleton-row" />
            </div>
        </div>

        <div v-else-if="showEmptyResultsState" class="source-search__states">
            <KsEmpty :background="false">
                <template #description>
                    <h3>{{ t('source_search.no_results_in_types', {query, types: selectedTypesLabel}) }}</h3>
                    <p v-if="hiddenTypeHint">{{ hiddenTypeHint }}</p>
                </template>
                <div class="source-search__examples">
                    <KsButton v-if="hiddenTypeCounts.length > 0" type="primary" @click="selectAllTypes">
                        {{ t('source_search.select_all_types') }}
                    </KsButton>
                    <KsButton v-if="caseSensitive" @click="caseSensitive = false">
                        {{ t('source_search.turn_off', {option: t('source_search.match_case')}) }}
                    </KsButton>
                    <KsButton v-if="wholeWord" @click="wholeWord = false">
                        {{ t('source_search.turn_off', {option: t('source_search.match_whole_word')}) }}
                    </KsButton>
                    <KsButton v-if="regexEnabled" @click="regexEnabled = false">
                        {{ t('source_search.turn_off', {option: t('source_search.use_regex')}) }}
                    </KsButton>
                </div>
            </KsEmpty>
        </div>

        <KsSplitter v-else class="source-search__splitter">
            <KsSplitterPanel min="20%" size="38%" key="results">
                <SourceSearchResults
                    ref="resultsRef"
                    :query="query"
                    :caseSensitive="caseSensitive"
                    :selectedTypes="selectedTypes"
                    :flowsStatus="crossResourceSearchStore.flows.status"
                    :flowsResults="crossResourceSearchStore.flows.results"
                    :filesStatus="crossResourceSearchStore.files.status"
                    :filesNamespaces="crossResourceSearchStore.files.namespaces"
                    :kvStatus="crossResourceSearchStore.kv.status"
                    :kvGroups="crossResourceSearchStore.kv.groups"
                    :secretsStatus="crossResourceSearchStore.secrets.status"
                    :secretsGroups="crossResourceSearchStore.secrets.groups"
                    :selectedKey="selectedKey"
                    :replaceMode="replaceOpen"
                    :selectedMatchKeys="selectedMatchKeys"
                    :replaceContext="replaceContext"
                    :truncatedTypes="truncatedTypes"
                    data-test="source-search-results-pane"
                    @select="onSelect"
                    @toggle-flow="onToggleFlow"
                    @toggle-match="onToggleMatch"
                    @replace-flow="onReplaceFlow"
                    @replace-match="onReplaceMatch"
                    @retry-namespace="onRetryNamespace"
                />
            </KsSplitterPanel>
            <KsSplitterPanel min="20%" key="preview">
                <SourceSearchPreview
                    :selection="selection"
                    :query="query"
                    :caseSensitive="caseSensitive"
                    :replaceMode="showDiffPreview"
                    :previewResponse="previewResponse"
                    :selectionSummary="showDiffPreview ? selectionSummary : null"
                    :readOnlyExcludedCount="flowsReadOnlyGroupCount"
                    :excludedFromReplaceCount="nonFlowExcludedMatchCount"
                    :kvEntry="selectedKvEntry"
                    data-test="source-search-preview-pane"
                    @cancel="previewResponse = null"
                    @replace-all="onConfirmReplaceAll"
                />
            </KsSplitterPanel>
        </KsSplitter>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, type Component} from "vue"
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
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import PencilOff from "vue-material-design-icons/PencilOff.vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import DatabaseOutline from "vue-material-design-icons/DatabaseOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import useRestoreUrl from "../../composables/useRestoreUrl"
    import {useToast} from "../../utils/toast"
    import {useCrossResourceSearchStore} from "../../stores/crossResourceSearch"
    import {computeSelectionSummary, distinctSkipReasons, type ReplaceContext} from "../../utils/sourceSearchDiff"
    import {SEARCH_RESOURCE_TYPES, crossSearchResultKey, searchViewState, type CrossSearchSelection, type SearchResourceType} from "../../utils/crossResourceSearch"

    import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
    import type {SourceSearchReplacePreviewResponse, SourceSearchReplaceApplyResponse, SourceSearchScope} from "@kestra-io/kestra-sdk"

    const {loadInit} = useRestoreUrl()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()
    const crossResourceSearchStore = useCrossResourceSearchStore()

    const resultsRef = ref<InstanceType<typeof SourceSearchResults> | null>(null)

    const selection = ref<CrossSearchSelection | null>(null)
    const selectedMatchKeys = ref<Set<string>>(new Set())
    const previewResponse = ref<SourceSearchReplacePreviewResponse | null>(null)
    const previewLoading = ref(false)
    const searchPending = ref(false)
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

    const TYPE_ICONS: Record<SearchResourceType, Component> = {
        flows: FileTreeOutline,
        files: FolderOpenOutline,
        kv: DatabaseOutline,
        secrets: LockOutline,
    }

    function typeIcon(type: SearchResourceType) {
        return TYPE_ICONS[type]
    }

    function typeLabel(type: SearchResourceType) {
        return t(`source_search.type_${type}`)
    }

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

    const selectedTypes = computed<SearchResourceType[]>({
        get: () => {
            const raw = route.query.types
            if (raw === undefined) return [...SEARCH_RESOURCE_TYPES]
            const requested = (Array.isArray(raw) ? raw : String(raw).split(",")) as string[]
            const known = requested.filter((value): value is SearchResourceType => (SEARCH_RESOURCE_TYPES as string[]).includes(value))
            return known.length > 0 ? known : [...SEARCH_RESOURCE_TYPES]
        },
        set: (value: SearchResourceType[]) => pushQuery((q) => {
            if (value.length === SEARCH_RESOURCE_TYPES.length) delete q.types
            else q.types = value.join(",")
        }, {replace: true}),
    })

    function setTypeSelected(type: SearchResourceType, checked: boolean) {
        if (checked) {
            if (!selectedTypes.value.includes(type)) selectedTypes.value = [...selectedTypes.value, type]
        } else {
            selectedTypes.value = selectedTypes.value.filter((value) => value !== type)
        }
    }

    function selectAllTypes() {
        selectedTypes.value = [...SEARCH_RESOURCE_TYPES]
    }

    function onNamespaceChange(val: any) {
        pushQuery((q) => {
            if (val === undefined || val === "" || val === null || (Array.isArray(val) && val.length === 0)) {
                delete q.namespace
            } else {
                q.namespace = val
            }
        })
    }

    const selectedKey = computed(() => selection.value ? crossSearchResultKey(selection.value) : null)

    const showDiffPreview = computed(() => previewResponse.value !== null)

    const truncatedTypes = computed(() => Object.fromEntries(
        SEARCH_RESOURCE_TYPES
            .filter((type) => crossResourceSearchStore.truncatedFor(type))
            .map((type) => [type, {shown: crossResourceSearchStore.countFor(type), total: crossResourceSearchStore.totalFor(type)!}]),
    ))

    const selectionSummary = computed(() => computeSelectionSummary(crossResourceSearchStore.flows.results, selectedMatchKeys.value))

    const visibleFlatSelections = computed(() => crossResourceSearchStore.flatSelections.filter((entry) => selectedTypes.value.includes(entry.type)))

    const summaryMatchCount = computed(() => selectedTypes.value.reduce((sum, type) => sum + crossResourceSearchStore.countFor(type), 0))
    const summaryResourceCount = computed(() => selectedTypes.value.reduce((sum, type) => sum + crossResourceSearchStore.resourceCountFor(type), 0))
    const summaryActiveTypeCount = computed(() => selectedTypes.value.filter((type) => crossResourceSearchStore.countFor(type) > 0).length)

    const anyCountingSelected = computed(() => selectedTypes.value.some((type) => crossResourceSearchStore.statusFor(type) === "counting"))
    const failedSelectedTypes = computed(() => selectedTypes.value.filter((type) => crossResourceSearchStore.statusFor(type) === "failed"))

    const viewState = computed(() => searchViewState({
        hasQuery: Boolean(query.value),
        loadInit: loadInit.value,
        searchPending: searchPending.value,
        anyCounting: anyCountingSelected.value,
        matchCount: summaryMatchCount.value,
    }))

    const showLoadingState = computed(() => viewState.value === "loading")
    const showEmptyResultsState = computed(() => viewState.value === "empty")

    const hiddenTypeCounts = computed(() => SEARCH_RESOURCE_TYPES
        .filter((type) => !selectedTypes.value.includes(type))
        .map((type) => ({type, count: crossResourceSearchStore.countFor(type)}))
        .filter((entry) => entry.count > 0))

    const hiddenTypeHint = computed(() => hiddenTypeCounts.value
        .map((entry) => t("source_search.no_results_hidden_type", {count: entry.count, type: typeLabel(entry.type)}))
        .join(" "))

    const selectedTypesLabel = computed(() => selectedTypes.value.map(typeLabel).join(", "))

    const flowsReadOnlyGroupCount = computed(() => crossResourceSearchStore.flows.results.filter((group) => !group.editable).length)
    const flowsReadOnlyMatchCount = computed(() => crossResourceSearchStore.flows.results
        .filter((group) => !group.editable)
        .reduce((sum, group) => sum + group.matches.length, 0))

    const nonFlowSearchOnlyExclusions = computed(() => (["files", "kv", "secrets"] as SearchResourceType[])
        .filter((type) => selectedTypes.value.includes(type))
        .map((type) => ({type, count: crossResourceSearchStore.countFor(type)}))
        .filter((entry) => entry.count > 0))

    const nonFlowExcludedMatchCount = computed(() => nonFlowSearchOnlyExclusions.value.reduce((sum, entry) => sum + entry.count, 0))
    const totalExcludedFromReplaceCount = computed(() => flowsReadOnlyMatchCount.value + nonFlowExcludedMatchCount.value)

    const filesProgressInfo = computed(() => {
        if (!selectedTypes.value.includes("files") || crossResourceSearchStore.files.status !== "counting") return null
        return {
            done: crossResourceSearchStore.filesNamespacesDone,
            total: crossResourceSearchStore.filesNamespacesTotal,
            failed: crossResourceSearchStore.filesNamespacesFailed.length,
        }
    })

    const showFlowOnlyToggleHint = computed(() => caseSensitive.value || wholeWord.value || regexEnabled.value)

    const selectedKvEntry = computed(() => {
        if (selection.value?.type !== "kv") return null
        const group = crossResourceSearchStore.kv.groups.find((g) => g.namespace === selection.value!.namespace)
        return group?.matches.find((match) => match.key === (selection.value as {key: string}).key) ?? null
    })

    function onSelect(value: CrossSearchSelection) {
        selection.value = value
    }

    function goToMatch(delta: number) {
        const list = visibleFlatSelections.value
        if (list.length === 0) return
        const currentKeyValue = selectedKey.value
        const currentIndex = currentKeyValue ? list.findIndex((entry) => crossSearchResultKey(entry) === currentKeyValue) : -1
        const nextIndex = (currentIndex + delta + list.length) % list.length
        selection.value = {...list[nextIndex]}
    }

    const matchNavLabel = computed(() => {
        const list = visibleFlatSelections.value
        if (list.length === 0) return t("source_search.match_nav_empty")
        const currentKeyValue = selectedKey.value
        const currentIndex = currentKeyValue ? list.findIndex((entry) => crossSearchResultKey(entry) === currentKeyValue) : -1
        return t("source_search.match_nav", {current: currentIndex + 1, total: list.length})
    })

    function toggleCollapseAll() {
        allCollapsed.value = !allCollapsed.value
        if (allCollapsed.value) {
            resultsRef.value?.collapseAll()
        } else {
            resultsRef.value?.expandAll()
        }
    }

    function onToggleFlow(value: {namespace: string; id: string; checked: boolean}) {
        const group = crossResourceSearchStore.flows.results.find((g) => g.namespace === value.namespace && g.id === value.id)
        if (!group) return
        const next = new Set(selectedMatchKeys.value)
        for (const match of group.matches) {
            const key = crossSearchResultKey({type: "flows", namespace: group.namespace, id: group.id, line: match.line, column: match.column})
            if (value.checked) next.add(key)
            else next.delete(key)
        }
        selectedMatchKeys.value = next
    }

    function onToggleMatch(value: {namespace: string; id: string; line: number; column: number; checked: boolean}) {
        const next = new Set(selectedMatchKeys.value)
        const key = crossSearchResultKey({type: "flows", namespace: value.namespace, id: value.id, line: value.line, column: value.column})
        if (value.checked) next.add(key)
        else next.delete(key)
        selectedMatchKeys.value = next
    }

    function onReplaceFlow(value: {namespace: string; id: string}) {
        return applyReplace([{namespace: value.namespace, id: value.id}])
    }

    function onRetryNamespace(value: {namespace: string}) {
        return crossResourceSearchStore.retryNamespaceFiles(value.namespace)
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
        const flowsToApply = crossResourceSearchStore.flows.results
            .filter((group) => group.editable && group.matches.some((match) => selectedMatchKeys.value.has(crossSearchResultKey({type: "flows", namespace: group.namespace, id: group.id, line: match.line, column: match.column}))))
            .map((group) => ({namespace: group.namespace, id: group.id}))
        return applyReplace(flowsToApply)
    }

    async function fetchResults() {
        if (!loadInit.value) return
        if (!query.value) {
            searchPending.value = false
            crossResourceSearchStore.reset()
            return
        }

        previewResponse.value = null
        searchPending.value = true

        try {
            await crossResourceSearchStore.search({
                types: SEARCH_RESOURCE_TYPES,
                query: query.value,
                namespace: namespaceFilter.value,
                ...searchFilters.value,
            })
        } finally {
            searchPending.value = false
        }
    }

    const debouncedFetch = debounce(fetchResults, 300)

    watch(
        () => [query.value, namespaceFilter.value, JSON.stringify(searchFilters.value)].join("|"),
        () => {
            // Synchronous, so the debounce window is already covered by the loading state.
            searchPending.value = Boolean(query.value)
            debouncedFetch()
        },
    )

    watch(
        () => [crossResourceSearchStore.flows.results, selectedTypes.value] as const,
        ([flowsResults, types]) => {
            selectedMatchKeys.value = types.includes("flows")
                ? new Set(flowsResults
                    .filter((group) => group.editable)
                    .flatMap((group) => group.matches.map((match) => crossSearchResultKey({type: "flows", namespace: group.namespace, id: group.id, line: match.line, column: match.column}))))
                : new Set()
        },
    )

    watch(visibleFlatSelections, (list) => {
        const currentKeyValue = selection.value ? crossSearchResultKey(selection.value) : null
        const stillValid = currentKeyValue !== null && list.some((entry) => crossSearchResultKey(entry) === currentKeyValue)
        if (!stillValid) {
            selection.value = list.length > 0 ? {...list[0]} : null
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

.source-search__toggle-hint {
    margin: 0;
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
}

.source-search__scope-row {
    display: flex;
    align-items: flex-start;
    flex-wrap: wrap;
    gap: var(--ks-spacing-3);
    margin-top: var(--ks-spacing-3);
    padding-top: var(--ks-spacing-3);
    border-top: 1px solid var(--ks-border-subtle);
}

.source-search__scope-label {
    flex: 0 0 auto;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);
    padding-top: var(--ks-spacing-2);
}

.source-search__pills {
    flex: 1 1 30rem;
    display: flex;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
    align-items: center;
    min-width: 0;
}

.source-search__pill-count {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    font-variant-numeric: tabular-nums;
}

.source-search__pill-spin {
    animation: source-search-pill-spin 1s linear infinite;
}

@keyframes source-search-pill-spin {
    to { transform: rotate(360deg); }
}

.source-search__pill--failed {
    color: var(--ks-text-error);
}

.source-search__filter-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-3);
    margin-top: var(--ks-spacing-3);
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

.source-search__type-failed-banner,
.source-search__rbac-banner,
.source-search__progress-banner {
    flex: 0 0 auto;
    border-radius: 0;
}

.source-search__alert-title {
    font-weight: 600;
    margin-bottom: var(--ks-spacing-1);
}

.source-search__exclusion-list {
    margin: var(--ks-spacing-2) 0 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
    color: var(--ks-text-secondary);
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
