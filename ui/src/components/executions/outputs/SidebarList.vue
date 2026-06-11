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
                                <KsTag>{{ section.items.length }}</KsTag>
                            </span>
                        </template>

                        <div
                            v-for="item in section.items"
                            :key="item.expression"
                            class="item"
                            :class="{'active': item.expression === selectedExpression}"
                            @click="$emit('select', item)"
                        >
                            <span class="key">{{ item.label }}</span>
                            <KsTag class="card-tag" size="small">{{ item.type }}</KsTag>
                            
                            <code class="preview">{{ item.preview }}</code>
                        </div>
                    </KsCollapseItem>
                </KsCollapse>
            </template>

            <KsEmpty v-else :description="$t('variable_explorer.empty')" />
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
        KsEmpty,
    } from "@kestra-io/design-system"

    export interface ExplorerItem {
        label: string;
        value: unknown;
        type: string;
        preview: string;
        expression: string;
        taskRunId?: string;
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

    defineEmits<{
        (e: "select", item: ExplorerItem): void;
    }>()

    const search = ref("")

    function matches(item: ExplorerItem, query: string): boolean {
        if (item.label.toLowerCase().includes(query)) return true
        return JSON.stringify(item.value ?? "").toLowerCase().includes(query)
    }

    const visibleSections = computed<ExplorerSection[]>(() => {
        const query = search.value.trim().toLowerCase()
        return props.sections
            .map((section) => ({
                ...section,
                items: query ? section.items.filter((item) => matches(item, query)) : section.items,
            }))
            .filter((section) => section.items.length > 0)
    })

    const sectionsKeys = computed(() => props.sections.map((s) => s.key))

    const openSections = ref<string[]>()

    watch(sectionsKeys, (newKeys) => {
        // By default, open all sections.
        openSections.value = newKeys
    }, {immediate: true})
</script>

<style scoped lang="scss">
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
}

.item {
    display: grid;
    grid-template-columns: 1fr auto;
    grid-template-areas:
            "key tag"
            "preview tag";
    cursor: pointer;
    margin-bottom: .5rem;
    background-color: var(--ks-bg-surface);
    border-radius: 4px;
    padding: .75rem 1rem;

    &:hover {
        background-color: var(--ks-border-default);
    }

    &.active {
        outline: 1px solid var(--ks-border-focus);
    }

    .key {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        grid-area: key;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .card-tag{
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
</style>
