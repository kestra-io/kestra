<template>
    <div class="sidebar">
        <div class="search">
            <KsSearch
                v-model="search"
                :placeholder="$t('variable_explorer.search_placeholder')"
            />
        </div>

        <KsScrollbar class="sections">
            <template v-if="visibleSections.length">
                <KsCollapse v-model="openSections" class="sections-collapse">
                    <KsCollapseItem
                        v-for="section in visibleSections"
                        :key="section.key"
                        :name="section.key"
                    >
                        <template #title>
                            <span class="section-title">
                                <span>{{ section.label }}</span>
                                <KsTag round size="small">{{ section.items.length }}</KsTag>
                            </span>
                        </template>

                        <div
                            v-for="item in section.items"
                            :key="item.expression"
                            class="item"
                            data-test="explorer-item"
                            :class="{'active': item.expression === selectedExpression}"
                            @click="$emit('select', item)"
                        >
                            <span class="key">{{ item.label }}</span>
                            <span class="card-tags">
                                <KsTag v-if="item.searchable" type="warning" size="small" data-test="searchable-tag">
                                    {{ $t("variable_explorer.searchable") }}
                                </KsTag>
                                <KsTag size="small">{{ item.type }}</KsTag>
                                <KsTag v-if="item.isExpression" size="small">
                                    {{ $t("variable_explorer.expression") }}
                                </KsTag>
                            </span>
                            
                            <code class="preview">{{ item.preview }}</code>
                        </div>
                    </KsCollapseItem>
                </KsCollapse>
            </template>

            <KsNoData v-else :title="$t('variable_explorer.empty')" />
        </KsScrollbar>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"

    import {
        KsSearch,
        KsScrollbar,
        KsCollapse,
        KsCollapseItem,
        KsTag,
    } from "@kestra-io/design-system"

    import {matchesExplorerItem} from "./explorerSearch"

    export interface ExplorerItem {
        label: string;
        value: unknown;
        type: string;
        preview: string;
        expression: string;
        isExpression?: boolean;
        searchable?: boolean;
        taskRunId?: string;
        searchText?: string;
    }

    export interface ExplorerSection {
        key: string;
        label: string;
        items: ExplorerItem[];
    }

    const props = defineProps<{
        sections: ExplorerSection[];
        // Expression of the currently selected item, used to highlight it.
        selectedExpression?: string;
    }>()

    const emit = defineEmits<{
        (e: "select", item: ExplorerItem): void;
        (e: "search-change", query: string): void;
    }>()

    const search = ref("")

    const visibleSections = computed<ExplorerSection[]>(() => {
        const query = search.value.trim()
        return props.sections
            .map((section) => ({
                ...section,
                items: query ? section.items.filter((item) => matchesExplorerItem(item, query)) : section.items,
            }))
            .filter((section) => section.items.length > 0)
    })

    const sectionsKeys = computed(() => props.sections.map((s) => s.key))

    const openSections = ref<string[]>()

    watch(sectionsKeys, (newKeys) => {
        // By default, open all sections.
        openSections.value = newKeys
    }, {immediate: true})

    watch(search, (query) => {
        emit("search-change", query)
    })
</script>

<style scoped>
.sidebar {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    min-height: 0;
}

.search {
    padding: var(--ks-spacing-4);
}

.sections {
    flex: 1 1 0;
    min-height: 0;
    padding: 0 var(--ks-spacing-2) var(--ks-spacing-4);
}

.sections-collapse{
    --kel-collapse-header-bg-color: transparent;
    --kel-collapse-content-bg-color: transparent;
}

.section-title {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    font-weight: 600;
}

.item {
    display: grid;
    grid-template-columns: minmax(0, 1fr) fit-content(60%);
    grid-template-areas:
            "key tag"
            "preview tag";
    cursor: pointer;
    margin-bottom: var(--ks-spacing-2);
    background-color: var(--ks-bg-surface);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-spacing-2);
    padding: var(--ks-spacing-3) var(--ks-spacing-4);

    &:hover {
        background-color: var(--ks-border-default);
    }

    &.active {
        outline: 1px solid var(--ks-border-focus);
    }

    .key {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        grid-area: key;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .card-tags {
        display: flex;
        flex-wrap: wrap;
        justify-content: flex-end;
        min-width: 0;
        gap: var(--ks-spacing-2);
        grid-area: tag;
        align-self: center;
    }

    .preview {
        grid-area: preview;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
}

@media (max-width: 767px) {
    .item {
        grid-template-columns: minmax(0, 1fr) fit-content(60%);
        align-items: center;
        min-height: 2.75rem;
    }

    .item .key {
        min-width: 0;
        white-space: nowrap;
    }
}
</style>
