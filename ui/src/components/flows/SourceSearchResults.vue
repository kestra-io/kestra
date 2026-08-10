<template>
    <div ref="rootEl" class="source-search-results" data-test="source-search-results">
        <KsCollapse
            v-model="expanded"
            class="results-collapse"
        >
            <KsCollapseItem
                v-for="group in results"
                :key="groupKey(group)"
                :name="groupKey(group)"
                class="result-group"
            >
                <template #title>
                    <div
                        class="result-group-header"
                        :class="{'result-group-header--selected': selectedKey?.startsWith(`${groupKey(group)}#`)}"
                        data-test="source-search-group-header"
                        @click.stop="emit('select', {namespace: group.namespace, id: group.id, line: group.matches[0]?.line ?? 0, column: group.matches[0]?.column ?? 0})"
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
                        @click="emit('select', {namespace: group.namespace, id: group.id, line: match.line, column: match.column})"
                        @keydown.enter="emit('select', {namespace: group.namespace, id: group.id, line: match.line, column: match.column})"
                        @keydown.space.prevent="emit('select', {namespace: group.namespace, id: group.id, line: match.line, column: match.column})"
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
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, nextTick} from "vue"
    import _escape from "lodash/escape"
    import Lock from "vue-material-design-icons/Lock.vue"
    import FindReplace from "vue-material-design-icons/FindReplace.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import {inlineReplacement, type ReplaceContext, type SourceSearchResult, type SourceMatch} from "../../utils/sourceSearchDiff"

    const props = defineProps<{
        results: SourceSearchResult[]
        selectedKey: string | null
        replaceMode: boolean
        selectedMatchKeys: Set<string>
        replaceContext?: ReplaceContext | null
    }>()

    const emit = defineEmits<{
        (e: "select", value: {namespace: string; id: string; line: number; column: number}): void
        (e: "toggle-flow", value: {namespace: string; id: string; checked: boolean}): void
        (e: "toggle-match", value: {namespace: string; id: string; line: number; column: number; checked: boolean}): void
        (e: "replace-flow", value: {namespace: string; id: string}): void
        (e: "replace-match", value: {namespace: string; id: string; line: number; column: number}): void
    }>()

    const SECRET_PATTERN = /secret\(\s*['"]([^'"]+)['"]\s*\)/

    const rootEl = ref<HTMLElement | null>(null)
    const expanded = ref<string[]>([])

    watch(
        () => props.selectedKey,
        async () => {
            await nextTick()
            rootEl.value?.querySelector(".result-match--selected")?.scrollIntoView({block: "nearest"})
        },
    )

    watch(
        () => props.results,
        (newResults) => {
            expanded.value = newResults.map(groupKey)
        },
        {immediate: true},
    )

    function groupKey(group: SourceSearchResult) {
        return `${group.namespace}.${group.id}`
    }

    function matchKey(group: SourceSearchResult, match: SourceMatch) {
        return `${groupKey(group)}#${match.line}:${match.column}`
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
        expanded.value = []
    }

    function expandAll() {
        expanded.value = props.results.map(groupKey)
    }

    defineExpose({collapseAll, expandAll})
</script>

<style scoped lang="scss">
.source-search-results {
    height: 100%;
    overflow-y: auto;
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
</style>
