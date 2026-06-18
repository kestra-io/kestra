<template>
    <div class="source-search-results" data-test="source-search-results">
        <KsEmpty v-if="!results || results.length === 0" />
        <KsCollapse
            v-else
            v-model="expanded"
            class="results-collapse"
        >
            <KsCollapseItem
                v-for="item in results"
                :key="`${item.model.namespace}.${item.model.id}`"
                :name="`${item.model.namespace}.${item.model.id}`"
                class="result-group"
            >
                <template #title>
                    <span
                        class="result-group-header"
                        :class="{'result-group-header--selected': selectedKey?.startsWith(`${item.model.namespace}.${item.model.id}#`)}"
                        data-test="source-search-group-header"
                        @click.stop="emit('select', {namespace: item.model.namespace, id: item.model.id, matchIndex: 0})"
                    >
                        <span class="result-group-namespace">{{ item.model.namespace }}.</span>
                        <span class="result-group-id">{{ item.model.id }}</span>
                        <span class="result-group-count">{{ t("source_search.match_count", {count: item.fragments.length}) }}</span>
                    </span>
                </template>

                <div class="result-fragments">
                    <div
                        v-for="(fragment, idx) in item.fragments"
                        :key="idx"
                        class="result-fragment"
                        :class="{'result-fragment--selected': selectedKey === `${item.model.namespace}.${item.model.id}#${idx}`}"
                        role="button"
                        tabindex="0"
                        data-test="source-search-match"
                        @click="emit('select', {namespace: item.model.namespace, id: item.model.id, matchIndex: idx})"
                        @keydown.enter="emit('select', {namespace: item.model.namespace, id: item.model.id, matchIndex: idx})"
                        @keydown.space.prevent="emit('select', {namespace: item.model.namespace, id: item.model.id, matchIndex: idx})"
                    >
                        <pre v-html="sanitize(fragment)" class="fragment-pre" />
                    </div>

                    <div class="result-open-link">
                        <router-link
                            :to="{path: `/flows/edit/${item.model.namespace}/${item.model.id}/source`}"
                            class="open-flow-link"
                            data-test="source-search-open-link"
                        >
                            {{ t("source_search.open_flow") }}
                            <KsIcon size="xs"><ArrowRight /></KsIcon>
                        </router-link>
                    </div>
                </div>
            </KsCollapseItem>
        </KsCollapse>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import _escape from "lodash/escape"
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue"

    const props = defineProps<{
        results: Array<{model: {namespace: string; id: string}; fragments: string[]}> | undefined
        selectedKey: string | null
    }>()

    const emit = defineEmits<{
        (e: "select", value: {namespace: string; id: string; matchIndex: number}): void
    }>()

    const {t} = useI18n()

    const expanded = ref<string[]>([])

    watch(
        () => props.results,
        (newResults) => {
            if (newResults) {
                expanded.value = newResults.map((item) => `${item.model.namespace}.${item.model.id}`)
            } else {
                expanded.value = []
            }
        },
        {immediate: true},
    )

    function sanitize(content: string) {
        return _escape(content)
            .replaceAll("[mark]", "<mark>")
            .replaceAll("[/mark]", "</mark>")
    }
</script>

<style scoped lang="scss">
.source-search-results {
    height: 100%;
    overflow-y: auto;
    padding: var(--ks-spacing-2);
}

.results-collapse {
    border: none;
}

.result-group {
    margin-bottom: var(--ks-spacing-1);
}

.result-group-header {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    width: 100%;
    padding-inline: var(--ks-spacing-3);
    cursor: pointer;

    &--selected {
        color: var(--ks-text-link);
    }
}

.result-group-namespace {
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-sm);
}

.result-group-id {
    color: var(--ks-text-primary);
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
}

.result-group-count {
    margin-left: auto;
    padding: var(--ks-spacing-1) var(--ks-spacing-2);
    background-color: var(--ks-bg-badge);
    border-radius: var(--ks-radius-sm);
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-xs);
    white-space: nowrap;
}

.result-fragments {
    padding: var(--ks-spacing-1) var(--ks-spacing-3);
}

.result-fragment {
    cursor: pointer;
    padding: var(--ks-spacing-1) 0;
    margin-bottom: var(--ks-spacing-1);

    &:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: -2px;
    }

    &:hover:not(.result-fragment--selected) .fragment-pre {
        box-shadow: inset 0 0 0 1px var(--ks-border-default);
    }

    &--selected .fragment-pre {
        box-shadow: inset 0 0 0 1px var(--ks-border-focus);
    }
}

.fragment-pre {
    margin: 0;
    font-size: var(--ks-font-size-xs);
    white-space: pre-wrap;
    word-break: break-all;
    color: var(--ks-text-secondary);
    border-radius: var(--ks-radius-base);

    :deep(mark) {
        background-color: var(--ks-status-background-warning);
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-xs);
        padding: 0 var(--ks-spacing-1);
    }
}

.result-open-link {
    padding: var(--ks-spacing-2) 0 var(--ks-spacing-1);
    font-size: var(--ks-font-size-xs);
}

.open-flow-link {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    color: var(--ks-text-link);
}
</style>

