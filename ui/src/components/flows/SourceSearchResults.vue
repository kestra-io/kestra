<template>
    <div ref="rootEl" class="source-search-results" data-test="source-search-results">
        <section
            v-for="type in visibleTypes"
            :key="type"
            class="type-section"
            data-test="source-search-type-section"
            :data-type="type"
        >
            <div class="type-header">
                <span class="type-icon"><component :is="typeIcon(type)" /></span>
                <span class="type-name">{{ typeLabel(type) }}</span>
                <span class="type-meta">{{ typeMeta(type) }}</span>
                <span v-if="statusFor(type) === 'counting'" class="type-status" role="status">
                    <Loading class="spin" />
                </span>
                <KsTag v-if="!replaceMode" size="small" :type="type === 'flows' ? undefined : 'info'" round>
                    <template v-if="type !== 'flows'" #icon>
                        <InformationOutline />
                    </template>
                    {{ typeTag(type) }}
                </KsTag>
                <KsTag v-else-if="type !== 'flows'" size="small" round>
                    <template #icon>
                        <PencilOff />
                    </template>
                    {{ $t('source_search.tag_search_only') }}
                </KsTag>
            </div>

            <template v-if="type === 'flows'">
                <KsCollapse v-model="expanded.flows" class="results-collapse">
                    <KsCollapseItem
                        v-for="group in flowsResults"
                        :key="groupKey(group)"
                        :name="groupKey(group)"
                        class="result-group"
                    >
                        <template #title>
                            <div
                                class="result-group-header"
                                :class="{'result-group-header--selected': selectedKey?.startsWith(`${groupKey(group)}#`)}"
                                data-test="source-search-group-header"
                                @click.stop="emit('select', {type: 'flows', namespace: group.namespace, id: group.id, line: group.matches[0]?.line ?? 0, column: group.matches[0]?.column ?? 0})"
                            >
                                <KsCheckbox
                                    v-if="replaceMode"
                                    class="result-group-checkbox"
                                    :modelValue="isGroupChecked(group)"
                                    :indeterminate="isGroupIndeterminate(group)"
                                    :disabled="!group.editable"
                                    :aria-label="group.editable
                                        ? $t('source_search.select_all_in_flow', {namespace: group.namespace, id: group.id})
                                        : $t('source_search.cannot_select_read_only', {namespace: group.namespace, id: group.id})"
                                    @click.stop
                                    @update:modelValue="(checked: boolean) => emit('toggle-flow', {namespace: group.namespace, id: group.id, checked})"
                                />
                                <span class="result-group-title">
                                    <span class="result-group-namespace">{{ group.namespace }} /</span>
                                    <span class="result-group-id">{{ group.id }}</span>
                                </span>
                                <KsTag size="small" round class="result-group-count" :aria-label="$t('source_search.match_count', {count: group.matches.length})">
                                    {{ group.matches.length }}
                                </KsTag>
                                <KsTag v-if="!group.editable" size="small" class="result-group-readonly" :title="$t('source_search.read_only_tooltip')">
                                    <template #icon>
                                        <Lock />
                                    </template>
                                    {{ $t('source_search.read_only') }}
                                </KsTag>
                                <span class="result-group-actions" :class="{'result-group-actions--persistent': replaceMode}">
                                    <KsButton
                                        v-if="replaceMode && group.editable"
                                        size="small"
                                        class="result-group-replace"
                                        @click.stop="emit('replace-flow', {namespace: group.namespace, id: group.id})"
                                    >
                                        <FindReplace class="result-group-replace-icon" />
                                        {{ $t('source_search.replace_all_in_flow') }}
                                    </KsButton>
                                    <router-link
                                        :to="{path: `/flows/edit/${group.namespace}/${group.id}/source`}"
                                        class="result-group-open-link"
                                        :aria-label="$t('source_search.open_flow')"
                                        :title="$t('source_search.open_flow')"
                                        data-test="source-search-open-link"
                                        @click.stop
                                    >
                                        <OpenInNew />
                                    </router-link>
                                </span>
                            </div>
                        </template>

                        <div class="result-matches">
                            <div
                                v-for="match in group.matches"
                                :key="matchKey(group, match)"
                                class="result-match"
                                :class="{'result-match--selected': selectedKey === matchKey(group, match)}"
                                role="button"
                                tabindex="0"
                                data-test="source-search-match"
                                @click="emit('select', {type: 'flows', namespace: group.namespace, id: group.id, line: match.line, column: match.column})"
                                @keydown.enter="emit('select', {type: 'flows', namespace: group.namespace, id: group.id, line: match.line, column: match.column})"
                                @keydown.space.prevent="emit('select', {type: 'flows', namespace: group.namespace, id: group.id, line: match.line, column: match.column})"
                            >
                                <KsCheckbox
                                    v-if="replaceMode"
                                    :modelValue="selectedMatchKeys.has(matchKey(group, match))"
                                    :disabled="!group.editable"
                                    :aria-label="$t('source_search.select_match', {line: match.line})"
                                    @click.stop
                                    @update:modelValue="(checked: boolean) => emit('toggle-match', {namespace: group.namespace, id: group.id, line: match.line, column: match.column, checked})"
                                />
                                <span class="result-match-lineno">{{ match.line }}</span>
                                <KsTag v-if="secretKey(match.snippet)" size="small" class="result-match-secret">
                                    <template #icon>
                                        <Lock />
                                    </template>
                                    secret('{{ secretKey(match.snippet) }}')
                                </KsTag>
                                <div v-else class="result-match-snippet">
                                    <code v-html="renderSnippet(match.snippet)" />
                                </div>
                                <KsButton
                                    v-if="replaceContext && group.editable && !secretKey(match.snippet)"
                                    size="small"
                                    type="primary"
                                    class="result-match-replace"
                                    :title="$t('source_search.replace_this_match')"
                                    @click.stop="emit('replace-match', {namespace: group.namespace, id: group.id, line: match.line, column: match.column})"
                                >
                                    {{ $t('source_search.replace_this') }}
                                </KsButton>
                            </div>
                        </div>
                    </KsCollapseItem>
                </KsCollapse>
            </template>

            <template v-else-if="type === 'files'">
                <KsCollapse v-model="expanded.files" class="results-collapse">
                    <KsCollapseItem
                        v-for="group in fileGroups"
                        :key="`files:${group.namespace}`"
                        :name="`files:${group.namespace}`"
                        class="result-group"
                    >
                        <template #title>
                            <div class="result-group-header" data-test="source-search-group-header" @click.stop="emit('select', {type: 'files', namespace: group.namespace, path: group.paths[0]})">
                                <span class="result-group-title">
                                    <span class="result-group-id">{{ group.namespace }}</span>
                                </span>
                                <KsTag size="small" round class="result-group-count">
                                    {{ group.paths.length }}
                                </KsTag>
                            </div>
                        </template>
                        <div class="result-matches">
                            <div
                                v-for="path in group.paths"
                                :key="rowKey({type: 'files', namespace: group.namespace, path})"
                                class="result-match"
                                :class="{'result-match--selected': selectedKey === rowKey({type: 'files', namespace: group.namespace, path})}"
                                role="button"
                                tabindex="0"
                                data-test="source-search-match"
                                @click="emit('select', {type: 'files', namespace: group.namespace, path})"
                                @keydown.enter="emit('select', {type: 'files', namespace: group.namespace, path})"
                            >
                                <span class="result-row-icon"><FileDocumentOutline /></span>
                                <span class="result-path">
                                    <span
                                        v-for="(segment, index) in buildPathSegments(path, query, caseSensitive)"
                                        :key="index"
                                        :class="{'result-path-dir': segment.dim}"
                                    ><mark v-if="segment.matched">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></span>
                                </span>
                            </div>
                        </div>
                    </KsCollapseItem>
                </KsCollapse>

                <div
                    v-for="namespaceState in pendingFileNamespaces"
                    :key="`pending:${namespaceState.namespace}`"
                    class="type-pending"
                    aria-busy="true"
                >
                    <Loading class="spin" />
                    <i18n-t keypath="source_search.searching_namespace" tag="span">
                        <template #namespace>
                            <code>{{ namespaceState.namespace }}</code>
                        </template>
                    </i18n-t>
                </div>

                <div
                    v-for="namespaceState in failedFileNamespaces"
                    :key="`failed:${namespaceState.namespace}`"
                    class="type-fail"
                >
                    <span class="type-fail-icon"><AlertCircleOutline /></span>
                    <span class="type-fail-text">
                        <i18n-t keypath="source_search.namespace_search_failed" tag="span">
                            <template #namespace>
                                <code>{{ namespaceState.namespace }}</code>
                            </template>
                        </i18n-t>
                        <span>{{ namespaceState.errorMessage || $t('source_search.namespace_search_failed_detail') }}</span>
                    </span>
                    <KsButton size="small" @click="emit('retry-namespace', {namespace: namespaceState.namespace})">
                        <Refresh />
                        {{ $t('source_search.retry_namespace') }}
                    </KsButton>
                </div>
            </template>

            <template v-else-if="type === 'kv'">
                <KsCollapse v-model="expanded.kv" class="results-collapse">
                    <KsCollapseItem
                        v-for="group in kvGroups"
                        :key="`kv:${group.namespace}`"
                        :name="`kv:${group.namespace}`"
                        class="result-group"
                    >
                        <template #title>
                            <div class="result-group-header" data-test="source-search-group-header" @click.stop="emit('select', {type: 'kv', namespace: group.namespace, key: group.matches[0]?.key})">
                                <span class="result-group-title">
                                    <span class="result-group-id">{{ group.namespace }}</span>
                                </span>
                                <KsTag size="small" round class="result-group-count">
                                    {{ group.matches.length }}
                                </KsTag>
                            </div>
                        </template>
                        <div class="result-matches">
                            <div
                                v-for="entry in group.matches"
                                :key="rowKey({type: 'kv', namespace: group.namespace, key: entry.key})"
                                class="result-match"
                                :class="{'result-match--selected': selectedKey === rowKey({type: 'kv', namespace: group.namespace, key: entry.key})}"
                                role="button"
                                tabindex="0"
                                data-test="source-search-match"
                                @click="emit('select', {type: 'kv', namespace: group.namespace, key: entry.key})"
                                @keydown.enter="emit('select', {type: 'kv', namespace: group.namespace, key: entry.key})"
                            >
                                <span class="result-row-icon"><DatabaseOutline /></span>
                                <span class="result-key" v-html="renderTermHighlighted(entry.key)" />
                                <span v-if="entry.updateDate" class="result-row-meta">
                                    <KsDateAgo :date="entry.updateDate" />
                                </span>
                            </div>
                        </div>
                    </KsCollapseItem>
                </KsCollapse>
            </template>

            <template v-else>
                <KsCollapse v-model="expanded.secrets" class="results-collapse">
                    <KsCollapseItem
                        v-for="group in secretsGroups"
                        :key="`secrets:${group.namespace}`"
                        :name="`secrets:${group.namespace}`"
                        class="result-group"
                    >
                        <template #title>
                            <div class="result-group-header" data-test="source-search-group-header" @click.stop="emit('select', {type: 'secrets', namespace: group.namespace, key: group.matches[0]?.key})">
                                <span class="result-group-title">
                                    <span class="result-group-id">{{ group.namespace }}</span>
                                </span>
                                <KsTag size="small" round class="result-group-count">
                                    {{ group.matches.length }}
                                </KsTag>
                            </div>
                        </template>
                        <div class="result-matches">
                            <div
                                v-for="entry in group.matches"
                                :key="rowKey({type: 'secrets', namespace: group.namespace, key: entry.key})"
                                class="result-match"
                                :class="{'result-match--selected': selectedKey === rowKey({type: 'secrets', namespace: group.namespace, key: entry.key})}"
                                role="button"
                                tabindex="0"
                                data-test="source-search-match"
                                @click="emit('select', {type: 'secrets', namespace: group.namespace, key: entry.key})"
                                @keydown.enter="emit('select', {type: 'secrets', namespace: group.namespace, key: entry.key})"
                            >
                                <KsTag size="small" class="result-secret-chip">
                                    <template #icon>
                                        <Lock />
                                    </template>
                                    <span v-html="renderHighlighted(entry.key)" />
                                </KsTag>
                            </div>
                        </div>
                    </KsCollapseItem>
                </KsCollapse>
            </template>
        </section>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch, nextTick, type Component} from "vue"
    import _escape from "lodash/escape"
    import Lock from "vue-material-design-icons/Lock.vue"
    import FindReplace from "vue-material-design-icons/FindReplace.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import DatabaseOutline from "vue-material-design-icons/DatabaseOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import PencilOff from "vue-material-design-icons/PencilOff.vue"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import Refresh from "vue-material-design-icons/Refresh.vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import {useI18n} from "vue-i18n"
    import {inlineReplacement, type ReplaceContext, type SourceSearchResult, type SourceMatch} from "../../utils/sourceSearchDiff"
    import {
        SEARCH_RESOURCE_TYPES,
        buildPathSegments,
        buildHighlightHtml,
        buildTermHighlightHtml,
        crossSearchResultKey,
        type CrossSearchSelection,
        type SearchResourceType,
        type SearchStatus,
    } from "../../utils/crossResourceSearch"
    import type {NamespaceFileState, KvMatchEntry, SecretMatchEntry, ResourceGroup} from "../../stores/crossResourceSearch"

    const props = defineProps<{
        query: string
        caseSensitive: boolean
        selectedTypes: SearchResourceType[]
        flowsStatus: SearchStatus
        flowsResults: SourceSearchResult[]
        filesStatus: SearchStatus
        filesNamespaces: NamespaceFileState[]
        kvStatus: SearchStatus
        kvGroups: ResourceGroup<KvMatchEntry>[]
        secretsStatus: SearchStatus
        secretsGroups: ResourceGroup<SecretMatchEntry>[]
        selectedKey: string | null
        replaceMode: boolean
        selectedMatchKeys: Set<string>
        replaceContext?: ReplaceContext | null
        truncatedTypes?: Partial<Record<SearchResourceType, {shown: number; total: number}>>
    }>()

    const emit = defineEmits<{
        (e: "select", value: CrossSearchSelection): void
        (e: "toggle-flow", value: {namespace: string; id: string; checked: boolean}): void
        (e: "toggle-match", value: {namespace: string; id: string; line: number; column: number; checked: boolean}): void
        (e: "replace-flow", value: {namespace: string; id: string}): void
        (e: "replace-match", value: {namespace: string; id: string; line: number; column: number}): void
        (e: "retry-namespace", value: {namespace: string}): void
    }>()

    const {t} = useI18n()

    const SECRET_PATTERN = /secret\(\s*['"]([^'"]+)['"]\s*\)/

    const rootEl = ref<HTMLElement | null>(null)
    const expanded = ref<Record<SearchResourceType, string[]>>({flows: [], files: [], kv: [], secrets: []})

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

    function statusFor(type: SearchResourceType): SearchStatus {
        switch (type) {
        case "flows": return props.flowsStatus
        case "files": return props.filesStatus
        case "kv": return props.kvStatus
        case "secrets": return props.secretsStatus
        }
    }

    const visibleTypes = computed(() => SEARCH_RESOURCE_TYPES.filter((type) => props.selectedTypes.includes(type) && statusFor(type) !== "idle" && statusFor(type) !== "failed"))

    function typeMeta(type: SearchResourceType) {
        const base = baseTypeMeta(type)
        const truncated = props.truncatedTypes?.[type]
        return truncated
            ? `${base} · ${t("source_search.truncated_type", truncated)}`
            : base
    }

    function baseTypeMeta(type: SearchResourceType) {
        switch (type) {
        case "flows":
            return t("source_search.type_meta", {
                matches: t("source_search.match_count", {count: flowsMatchCount.value}),
                resources: t("source_search.count_flows", {count: props.flowsResults.length}),
            })
        case "files":
            return t("source_search.type_meta", {
                matches: t("source_search.count_paths", {count: filesMatchCount.value}),
                resources: t("source_search.count_namespaces", {count: fileGroups.value.length}),
            })
        case "kv":
            return t("source_search.type_meta", {
                matches: t("source_search.count_keys", {count: kvMatchCount.value}),
                resources: t("source_search.count_namespaces", {count: props.kvGroups.length}),
            })
        case "secrets":
            return t("source_search.type_meta", {
                matches: t("source_search.count_keys", {count: secretsMatchCount.value}),
                resources: t("source_search.count_namespaces", {count: props.secretsGroups.length}),
            })
        }
    }

    function typeTag(type: SearchResourceType) {
        switch (type) {
        case "flows": return t("source_search.tag_source_code")
        case "files": return t("source_search.tag_paths_only")
        case "kv": return t("source_search.tag_keys_only_values")
        case "secrets": return t("source_search.tag_keys_only_never")
        }
    }

    const flowsMatchCount = computed(() => props.flowsResults.reduce((sum, group) => sum + group.matches.length, 0))
    const fileGroups = computed(() => props.filesNamespaces.filter((n) => n.status === "done" && n.paths.length > 0))
    const pendingFileNamespaces = computed(() => props.filesNamespaces.filter((n) => n.status === "pending"))
    const failedFileNamespaces = computed(() => props.filesNamespaces.filter((n) => n.status === "failed"))
    const filesMatchCount = computed(() => props.filesNamespaces.reduce((sum, n) => sum + n.paths.length, 0))
    const kvMatchCount = computed(() => props.kvGroups.reduce((sum, group) => sum + group.matches.length, 0))
    const secretsMatchCount = computed(() => props.secretsGroups.reduce((sum, group) => sum + group.matches.length, 0))

    function rowKey(selection: CrossSearchSelection) {
        return crossSearchResultKey(selection)
    }

    function renderHighlighted(text: string) {
        return buildHighlightHtml(text, props.query, props.caseSensitive)
    }

    // KV matches on query terms server-side, so highlight per term or a legitimate row renders unmarked.
    function renderTermHighlighted(text: string) {
        return buildTermHighlightHtml(text, props.query, props.caseSensitive)
    }

    watch(
        () => [props.flowsResults, props.filesNamespaces, props.kvGroups, props.secretsGroups],
        () => {
            expanded.value = {
                flows: props.flowsResults.map(groupKey),
                files: fileGroups.value.map((n) => `files:${n.namespace}`),
                kv: props.kvGroups.map((g) => `kv:${g.namespace}`),
                secrets: props.secretsGroups.map((g) => `secrets:${g.namespace}`),
            }
        },
        {immediate: true},
    )

    watch(
        () => props.selectedKey,
        async () => {
            await nextTick()
            rootEl.value?.querySelector(".result-match--selected")?.scrollIntoView({block: "nearest"})
        },
    )

    function groupKey(group: SourceSearchResult) {
        return `flows:${group.namespace}.${group.id}`
    }

    function matchKey(group: SourceSearchResult, match: SourceMatch) {
        return crossSearchResultKey({type: "flows", namespace: group.namespace, id: group.id, line: match.line, column: match.column})
    }

    function isGroupChecked(group: SourceSearchResult) {
        return group.matches.length > 0 && group.matches.every((match) => props.selectedMatchKeys.has(matchKey(group, match)))
    }

    function isGroupIndeterminate(group: SourceSearchResult) {
        const checkedCount = group.matches.filter((match) => props.selectedMatchKeys.has(matchKey(group, match))).length
        return checkedCount > 0 && checkedCount < group.matches.length
    }

    function secretKey(snippet: string) {
        return stripMarkers(snippet).match(SECRET_PATTERN)?.[1] ?? null
    }

    function stripMarkers(snippet: string) {
        return snippet.replaceAll("[mark]", "").replaceAll("[/mark]", "")
    }

    function renderSnippet(snippet: string) {
        return snippet
            .split(/(\[mark\][\s\S]*?\[\/mark\])/)
            .map((part) => {
                const marked = part.match(/^\[mark\]([\s\S]*)\[\/mark\]$/)
                if (!marked) {
                    return _escape(part)
                }
                const old = marked[1]
                if (!props.replaceContext) {
                    return `<mark>${_escape(old)}</mark>`
                }
                const next = inlineReplacement(old, props.replaceContext)
                const removed = `<del class="result-match-old">${_escape(old)}</del>`
                if (next === "") {
                    return removed
                }
                return `${removed}<span class="result-match-arrow" aria-hidden="true"> → </span><ins class="result-match-new">${_escape(next)}</ins>`
            })
            .join("")
    }

    function collapseAll() {
        expanded.value = {flows: [], files: [], kv: [], secrets: []}
    }

    function expandAll() {
        expanded.value = {
            flows: props.flowsResults.map(groupKey),
            files: fileGroups.value.map((n) => `files:${n.namespace}`),
            kv: props.kvGroups.map((g) => `kv:${g.namespace}`),
            secrets: props.secretsGroups.map((g) => `secrets:${g.namespace}`),
        }
    }

    defineExpose({collapseAll, expandAll})
</script>

<style scoped lang="scss">
.source-search-results {
    height: 100%;
    overflow-y: auto;
}

.type-section {
    border-bottom: 1px solid var(--ks-border-subtle);
}

.type-header {
    position: sticky;
    top: 0;
    z-index: 2;
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    background: var(--ks-bg-elevated);
    border-bottom: 1px solid var(--ks-border-default);
    flex-wrap: wrap;
}

.type-icon {
    color: var(--ks-icon-default);
    display: inline-flex;
}

.type-name {
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    color: var(--ks-text-primary);
}

.type-meta {
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    font-variant-numeric: tabular-nums;
}

.type-status {
    display: inline-flex;
}

.spin {
    animation: source-search-spin 1s linear infinite;
    transform-origin: 50% 50%;
}

@keyframes source-search-spin {
    to { transform: rotate(360deg); }
}

.type-pending {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2) var(--ks-spacing-3) var(--ks-spacing-2) var(--ks-spacing-6);
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);

    code {
        font-family: var(--ks-font-family-mono);
    }
}

.type-fail {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-3) var(--ks-spacing-3) var(--ks-spacing-3) var(--ks-spacing-6);
    background: var(--ks-bg-error);
}

.type-fail-icon {
    color: var(--ks-status-error);
    flex: 0 0 auto;
}

.type-fail-text {
    flex: 1 1 auto;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
    display: flex;
    flex-direction: column;

    code {
        font-family: var(--ks-font-family-mono);
    }

    span {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }
}

.results-collapse {
    border: none;
}

.result-group {
    margin-bottom: 0;
}

.result-group-header {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    width: 100%;
    cursor: pointer;
}

.result-group-header--selected {
    color: var(--ks-text-link);
}

.result-group-checkbox {
    flex: 0 0 auto;
}

.result-group-title {
    display: flex;
    align-items: baseline;
    gap: var(--ks-spacing-1);
    min-width: 0;
    flex: 1 1 auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.result-group-namespace {
    color: var(--ks-text-muted);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
}

.result-group-id {
    color: var(--ks-text-primary);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
}

.result-group-count {
    flex: 0 0 auto;
    font-variant-numeric: tabular-nums;
}

.result-group-readonly {
    flex: 0 0 auto;
    color: var(--ks-status-warning);
    white-space: nowrap;
}

.result-group-actions {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    flex: 0 0 auto;
    opacity: 0;
    transition: opacity .1s ease;
}

.result-group-header:hover .result-group-actions {
    opacity: 1;
}

.result-group-actions--persistent {
    opacity: 1;
}

.result-group-replace {
    white-space: nowrap;
}

.result-match-replace {
    flex: 0 0 auto;
    white-space: nowrap;
}

.result-group-replace-icon {
    display: inline-flex;
    margin-right: var(--ks-spacing-1);
}

.result-group-open-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    border-radius: var(--ks-radius-xs);
    color: var(--ks-text-secondary);

    &:hover {
        background: var(--ks-bg-hover);
        color: var(--ks-text-primary);
    }
}

.result-matches {
    padding-block: var(--ks-spacing-1);
}

.result-match {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-1) var(--ks-spacing-3) var(--ks-spacing-1) var(--ks-spacing-6);
    cursor: pointer;
    position: relative;

    &:hover {
        background: var(--ks-bg-hover);
    }

    &:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: -2px;
    }
}

.result-match--selected {
    background: var(--ks-bg-active);
}

.result-match--selected::before {
    content: "";
    position: absolute;
    inset-block: 0;
    inset-inline-start: 0;
    width: 2px;
    background: var(--ks-border-focus);
}

.result-match-lineno {
    color: var(--ks-text-muted);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    min-width: 2.5ch;
    text-align: right;
    flex: 0 0 auto;
}

.result-match-snippet {
    flex: 1 1 auto;
    min-width: 0;

    code {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
        white-space: pre-wrap;
        overflow-wrap: anywhere;
        color: var(--ks-text-dim);

        :deep(mark) {
            background-color: var(--ks-status-background-warning);
            color: var(--ks-text-primary);
            border-radius: var(--ks-radius-xs);
            padding: 0 var(--ks-spacing-1);
        }

        :deep(.result-match-old) {
            background-color: var(--ks-status-background-failed);
            color: var(--ks-text-error);
            text-decoration: line-through;
            border-radius: var(--ks-radius-xs);
            padding: 0 var(--ks-spacing-1);
        }

        :deep(.result-match-new) {
            background-color: var(--ks-status-background-success);
            color: var(--ks-text-success);
            border-radius: var(--ks-radius-xs);
            padding: 0 var(--ks-spacing-1);
        }

        :deep(.result-match-arrow) {
            color: var(--ks-text-muted);
        }
    }
}

.result-match-secret {
    font-family: var(--ks-font-family-mono);
    color: var(--ks-text-secondary);
}

.result-row-icon {
    color: var(--ks-icon-muted);
    display: inline-flex;
    flex: 0 0 auto;
}

.result-row-meta {
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    flex: 0 0 auto;
    white-space: nowrap;
}

.result-path,
.result-key {
    flex: 1 1 auto;
    min-width: 0;
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    :deep(mark) {
        background-color: var(--ks-status-background-warning);
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-xs);
        padding: 0 var(--ks-spacing-1);
    }
}

.result-path-dir {
    color: var(--ks-text-secondary);
}

.result-secret-chip {
    font-family: var(--ks-font-family-mono);
    color: var(--ks-text-primary);
    max-width: 100%;

    :deep(mark) {
        background-color: var(--ks-status-background-warning);
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-xs);
        padding: 0 var(--ks-spacing-1);
    }
}
</style>
