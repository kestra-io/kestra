<template>
    <div class="search-container" ref="searchContainer">
        <el-input
            v-model="searchQuery"
            :placeholder="$t('search_docs')"
            class="search-input"
            @input="handleSearch"
            @keydown.enter.prevent="handleEnterKey"
            @keydown.up.prevent="handleKeyUp"
            @keydown.down.prevent="handleKeyDown"
            :loading="loading"
        >
            <template #prefix>
                <Magnify class="search-icon" />
            </template>
        </el-input>
        <div v-if="loading" class="loading-indicator">
            {{ $t('searching') }}
        </div>
        <div v-if="showResults" class="search-results">
            <template v-if="searchResults.length > 0">
                <ContextDocsLink
                    v-for="(result, index) in searchResults"
                    :key="result.url"
                    class="search-result"
                    :class="{'selected': index === selectedIndex}"
                    :href="result.parsedUrl.replace(/^docs\//, '')"
                    useRaw
                    :data-index="index"
                    @click="resetSearch"
                >
                    <h4 class="result-title">
                        {{ result.title }}
                    </h4>
                    <p class="result-preview">
                        {{ result.preview }}
                    </p>
                </ContextDocsLink>
            </template>
            <div v-else class="no-results">
                {{ $t("no_results_found") }}
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted} from "vue";
    import {useDocStore} from "../../stores/doc";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import ContextDocsLink from "./ContextDocsLink.vue";
    import {debounce} from "lodash-es";

    const docStore = useDocStore();

    const searchQuery = ref("");
    const searchResults = ref<Array<{ title: string; preview: string; url: string; parsedUrl: string }>>([]);
    const loading = ref(false);
    const selectedIndex = ref(0);
    const searchContainer = ref<HTMLDivElement | null>(null);

    const showResults = computed(() => {
        return searchQuery.value.trim().length > 0;
    });

    const handleKeyUp = (e: KeyboardEvent) => {
        e.preventDefault();
        if (searchResults.value.length > 0) {
            selectedIndex.value = Math.max(0, selectedIndex.value - 1);
        }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
        e.preventDefault();
        if (searchResults.value.length > 0) {
            selectedIndex.value = Math.min(searchResults.value.length - 1, selectedIndex.value + 1);
        }
    };

    const handleEnterKey = (e: KeyboardEvent) => {
        e.preventDefault();
        if (searchResults.value.length > 0) {
            const selectedResult = document.querySelector(`.search-result[data-index="${selectedIndex.value}"]`) as HTMLElement;
            if (selectedResult) {
                selectedResult.click();
            }
        }
    };

    const resetSearch = () => {
        searchQuery.value = "";
        searchResults.value = [];
    };

    const performSearch = async (query: string) => {
        if (!query) {
            searchResults.value = [];
            selectedIndex.value = 0;
            return;
        }

        try {
            loading.value = true;
            const results = await docStore.search({q: query, scoredSearch: true});

            const processedResults = (results || []).slice(0, 10);
            searchResults.value = processedResults;
            selectedIndex.value = 0;
        } catch (error) {
            console.error("Error searching docs:", error);
            searchResults.value = [];
            selectedIndex.value = 0;
        } finally {
            loading.value = false;
        }
    };

    const debouncedSearch = debounce(performSearch, 500);

    const handleSearch = () => {
        debouncedSearch(searchQuery.value.trim());
    };

    const handleClickOutside = (event: MouseEvent) => {
        if (searchContainer.value && !searchContainer.value.contains(event.target as Node)) {
            resetSearch();
        }
    };

    onMounted(() => {
        document.addEventListener("click", handleClickOutside);
    });

    onUnmounted(() => {
        document.removeEventListener("click", handleClickOutside);
        debouncedSearch.cancel();
    });
</script>

<style scoped lang="scss">
    .search-container {
        position: relative;
        margin-bottom: 0;
        z-index: 1001;
        padding-top: 12px;
        padding-left: 28px;
        padding-right: 28px;
    }

    .search-input {
        width: 100%;
    }
    .el-input__wrapper {
        background-color: var(--ks-background-input);
        box-shadow: 0 0 0 1px var(--ks-border-color);
        border-radius: 6px;
        padding: 0.5rem;
        transition: box-shadow 0.2s ease;

        &.is-focus {
            box-shadow: 0 0 0 1px var(--ks-primary);
        }
    }

    .el-input__inner {
        color: var(--ks-content-primary);
        font-size: 14px;
        height: 1.25rem;
        background: transparent;
    }

    .el-input__inner::placeholder {
        color: var(--ks-content-secondary);
    }

    .el-input__prefix {
        margin-right: 0.5rem;
    }

    .search-icon {
        font-size: 1rem;
        color: var(--ks-content-tertiary);
    }

    .loading-indicator {
        position: absolute;
        right: 2rem;
        top: 60%;
        transform: translateY(-50%);
        color: var(--ks-content-secondary);
        font-size: 14px;
    }

    .search-results {
        position: absolute;
        top: 100%;
        left: 26px;
        right: 26px;
        background-color: var(--ks-background-card);
        border-radius: 6px;
        margin-top: 4px;
        max-height: 400px;
        overflow-y: auto;
        z-index: 1001;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        padding: 4px 0;
    }

    .search-result {
        padding: 6px 12px;
        cursor: pointer;
        display: block;
        text-decoration: none;
        color: inherit;
        background: var(--ks-background-card);
        transition: background-color 0.2s;

        &:hover {
            background: var(--ks-background-hover);
            text-decoration: none;
            color: inherit;
        }

        &.selected {
            background: rgba(132, 5, 255, 0.1);
            border-left: 3px solid #8405FF;
        }

        .result-title {
            font-weight: 400;
            color: var(--ks-content-primary);
            margin-bottom: 2px;
            font-size: 14px;
        }

        .result-preview {
            font-size: 12px;
            color: var(--ks-content-secondary);
            margin: 0;
            opacity: 0.8;
        }
    }

    .no-results {
        color: var(--ks-content-secondary);
        text-align: center;
        cursor: default;
        padding: 6px 12px;
        font-size: 14px;

        &:hover {
            background: none;
        }
    }
</style>