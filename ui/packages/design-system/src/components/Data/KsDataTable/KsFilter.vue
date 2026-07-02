<template>
    <section class="filter">
        <div class="top">
            <MobileFilter v-if="isMobile">
                <template v-if="$slots.extra" #extra>
                    <slot name="extra" />
                </template>
            </MobileFilter>
            <template v-else>
                <KsButton
                    v-if="hasFilterKeys"
                    :icon="CodeTags"
                    size="default"
                    class="code-toggle"
                    :class="{'code-toggle--active': viewMode === 'raw'}"
                    :disabled="readOnly || codeToggleDisabled"
                    :aria-label="$t(viewMode === 'raw' ? 'filter.chip_view' : 'filter.raw_view')"
                    :title="$t(viewMode === 'raw' ? 'filter.chip_view' : 'filter.raw_view')"
                    @click="toggleViewMode"
                />
                <MainFilter v-if="viewMode === 'chip'" />
                <RawFilter v-else>
                    <template v-if="$slots.rawEditor" #rawEditor="slotProps">
                        <slot name="rawEditor" v-bind="slotProps" />
                    </template>
                </RawFilter>
                <RightFilter>
                    <template #extra>
                        <slot name="extra" />
                    </template>
                </RightFilter>
            </template>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, provide, onMounted, watch} from "vue"
    import {useMediaQuery} from "@vueuse/core"
    import type {
        AppliedFilter,
        FilterConfiguration,
        SavedFilter,
        TableOptions,
        TableProperties,
    } from "./filter/utils/filterTypes"
    import {useFilters} from "./filter/composables/useFilters"
    import {useSavedFilters} from "./filter/composables/useSavedFilters"
    import {useDataOptions} from "./filter/composables/useDataOptions"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./filter/utils/filterInjectionKeys.ts"
    import MainFilter from "./filter/MainFilter.vue"
    import MobileFilter from "./filter/MobileFilter.vue"
    import RawFilter from "./filter/RawFilter.vue"
    import RightFilter from "./filter/RightFilter.vue"
    import CodeTags from "vue-material-design-icons/CodeTags.vue"

    const props = withDefaults(defineProps<{
        configuration: FilterConfiguration;
        buttons?: {
            savedFilters?: {shown?: boolean};
            tableOptions?: {shown?: boolean}
        };
        tableOptions?: TableOptions;
        properties?: TableProperties;
        prefix?: string;
        showSearchInput?: boolean;
        searchInputFullWidth?: boolean;
        readOnly?: boolean;
        defaultScope?: boolean;
        defaultTimeRange?: boolean;
        defaultDuration?: string;
        /**
         * Initial view mode. `'chip'` (default) shows the structured filter UI;
         * `'raw'` opens the URL editor. Either way, the user can toggle via the
         * `{ }` / `≡` button — unless the URL has nesting the chip UI can't render,
         * in which case the view is locked on raw.
         */
        defaultViewMode?: "chip" | "raw";
    }>(), {
        buttons: () => ({}),
        tableOptions: () => ({}),
        properties: () => ({shown: false}),
        prefix: "",
        showSearchInput: true,
        searchInputFullWidth: false,
        readOnly: false,
        defaultScope: undefined,
        defaultTimeRange: undefined,
        defaultDuration: undefined,
        defaultViewMode: "chip",
    })

    const emits = defineEmits<{
        refresh: [];
        search: [query: string];
        filter: [filters: AppliedFilter[]];
        updateProperties: [columns: string[]];
    }>()

    const isMobile = useMediaQuery("(max-width: 768px)")

    const {
        appliedFilters,
        groups,
        topLogical,
        hasUnrenderableFilters,
        rawQuery,
        applyRawQuery,
        hasDismissedDefaultVisibleKeys,
        searchQuery,
        addFilter,
        removeFilter,
        updateFilter,
        moveFilter,
        placeFilter,
        wrapGroups,
        unwrapGroup,
        setTopLogical,
        setWrapperLogical,
        addGroup,
        removeGroup,
        resetToDefaults,
        clearFilters,
        hasPreApplied,
        getPreApplied,
    } = useFilters(
        props.configuration,
        props.showSearchInput,
        props.defaultScope,
        props.defaultTimeRange,
        props.defaultDuration,
    )

    const {savedFilters, saveFilter, updateSavedFilter, deleteSavedFilter} = useSavedFilters(
        props.prefix,
    )

    const {chartVisible, updateChart, refreshData: tableRefreshData} = useDataOptions(
        props.tableOptions,
    )

    const editingFilter = ref<SavedFilter | undefined>(undefined)

    /** View mode: 'chip' is the structured UI; 'raw' shows the URL query in an editor. */
    const viewMode = ref<"chip" | "raw">(props.defaultViewMode)
    const setViewMode = (mode: "chip" | "raw") => {
        viewMode.value = mode
    }
    // Auto-switch to raw view when the URL contains filters the chip UI can't render.
    watch(hasUnrenderableFilters, (unrenderable) => {
        if (unrenderable && viewMode.value === "chip") {
            viewMode.value = "raw"
        }
    }, {immediate: true})

    const toggleViewMode = () => setViewMode(viewMode.value === "chip" ? "raw" : "chip")
    const codeToggleDisabled = computed(() => viewMode.value === "raw" && hasUnrenderableFilters.value)

    const hasFilterKeys = computed(() => props.configuration.keys?.length > 0)
    const hasAppliedFilters = computed(() => appliedFilters.value?.length > 0)

    const loadSavedFilter = (savedFilter: SavedFilter) => {
        appliedFilters.value.forEach((filter) => {
            removeFilter(filter.id)
        })

        savedFilter.filters.forEach((filter) => {
            addFilter(filter)
        })
    }

    const refreshData = () => {
        tableRefreshData()
        emits("refresh")
    }

    provide(FILTER_CONTEXT_INJECTION_KEY, {
        configuration: computed(() => props.configuration),
        appliedFilters,
        groups,
        topLogical,
        hasUnrenderableFilters,
        rawQuery,
        viewMode,
        searchQuery,
        savedFilters,
        editingFilter,
        hasFilterKeys,
        hasAppliedFilters,
        hasDismissedDefaultVisibleKeys,
        buttons: computed(() => props.buttons),
        readOnly: computed(() => props.readOnly),
        properties: computed(() => props.properties),
        tableOptions: computed(() => props.tableOptions),
        showSearchInput: computed(() => props.showSearchInput),
        searchInputFullWidth: computed(() => props.searchInputFullWidth),
        chartVisible,
        addFilter,
        removeFilter,
        updateFilter,
        moveFilter,
        placeFilter,
        wrapGroups,
        unwrapGroup,
        setTopLogical,
        setWrapperLogical,
        applyRawQuery,
        setViewMode,
        addGroup,
        removeGroup,
        saveFilter,
        updateSavedFilter,
        deleteSavedFilter,
        loadSavedFilter,
        updateChart,
        refreshData,
        resetToDefaults,
        clearFilters,
        hasPreApplied,
        getPreApplied,
        editSavedFilter: (filter: SavedFilter) => {
            editingFilter.value = filter
        },
        closeEditFilter: () => {
            editingFilter.value = undefined
        },
        updateProperties: (columns: string[]) => {
            emits("updateProperties", columns)
        },
    })

    onMounted(() => {
        if (props.showSearchInput && searchQuery.value) {
            emits("search", searchQuery.value)
        }
        if (appliedFilters.value.length > 0) {
            emits("filter", appliedFilters.value)
        }
    })

    watch(searchQuery, (newQuery) => {
        if (props.showSearchInput) {
            emits("search", newQuery)
        }
    })

    watch(appliedFilters, (newFilters) => {
        emits("filter", newFilters)
    }, {deep: true})

</script>

<style lang="scss" scoped>
.filter {
    display: flex;
    flex-direction: column;
    margin-bottom: 1rem;
    width: 100%;
    border-radius: 0.5rem;

    .top {
        display: flex;
        align-items: flex-start;
        flex-wrap: wrap;
        gap: 0.5rem;

}

    .code-toggle {
        margin: 0 !important;
        flex-shrink: 0;
        background-color: var(--ks-btn-secondary-bg-default);
        box-shadow: 0 1px 2px var(--ks-shadow-surface);

        :deep(svg) {
            color: var(--ks-text-dim) !important;
            font-size: var(--ks-font-size-md);
        }

        &:hover {
            background-color: var(--ks-btn-secondary-bg-hover);
        }

        &--active {
            background-color: var(--ks-btn-secondary-bg-active);
            border-color: var(--ks-btn-secondary-border-active);

            :deep(svg) {
                color: var(--ks-content-link, var(--ks-text-link)) !important;
            }
        }
    }
}
</style>