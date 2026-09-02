<template>
    <KsAutocomplete
        ref="search"
        class="flex-shrink-0"
        v-model="query"
        :fetchSuggestions="search"
        popperClass="doc-toc-search-popper"
        :placeholder="$t('search')"
    >
        <template #prefix>
            <Magnify />
        </template>
        <template #default="{item}">
            <RouterLink
                :to="{path: '/' + item.parsedUrl}"
                class="d-flex gap-2"
            >
                {{ item.title }}
                <ArrowRight class="is-justify-end" />
            </RouterLink>
        </template>
    </KsAutocomplete>
    <ul class="toc d-flex flex-column gap-3">
        <li v-for="{section, children} in sectionsWithChildren" :key="section">
            <span class="text-secondary">
                {{ section.toUpperCase() }}
            </span>
            <RecursiveToc :parent="{children}" />
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useDocStore} from "../../stores/doc"
    import RecursiveToc from "./RecursiveToc.vue"
    import {buildDocsSections, buildDocsToc} from "./docsUtils"
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue"
    import Magnify from "vue-material-design-icons/Magnify.vue"

    interface SearchResult {
        parsedUrl: string;
        title: string;
    }

    const docStore = useDocStore()

    const rawStructure = ref<Record<string, any> | undefined>(undefined)
    const query = ref<string>("")

    const sectionsWithChildren = computed(() => buildDocsSections(buildDocsToc(rawStructure.value)))

    watch(
        () => docStore.resourceUrlTemplate,
        async (resourceUrlTemplate) => {
            if (!resourceUrlTemplate) return
            rawStructure.value = await docStore.children()
        },
        {immediate: true},
    )

    const search = async (q: string, cb: (results: SearchResult[]) => void) => {
        cb(await docStore.search({q}))
    }
</script>

<style lang="scss" scoped>
    .toc {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        list-style: none;
        padding-left: 0;
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &::-webkit-scrollbar {
            width: 6px !important;
        }

        &::-webkit-scrollbar-track,
        &::-webkit-scrollbar-thumb {
            background: transparent !important;
            border: none !important;
        }

        &:hover {
            scrollbar-color: var(--ks-border-default) transparent;

            &::-webkit-scrollbar-thumb {
                background: var(--ks-border-default) !important;
            }
        }
    }

    ul > li > span:first-child {
        display: block;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        letter-spacing: 0.05em;
    }

    .toc :deep(a[class*="depth-"]), .toc :deep(span[class*="depth-"]) {
        display: block;
        width: 100%;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        margin-bottom: 0.125rem;
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-primary);
        text-decoration: none;

        @for $i from 0 through 5 {
            &.depth-#{$i} {
                padding-left: calc(var(--ks-spacing-2) + #{$i} * var(--ks-spacing-4));

                @if $i == 0 {
                    font-weight: var(--ks-font-weight-medium);
                } @else {
                    font-size: var(--ks-font-size-xs);
                    color: var(--ks-text-secondary);
                }
            }
        }

        &:hover {
            color: var(--ks-text-link);
            background-color: var(--ks-bg-hover);
        }

        &.router-link-exact-active {
            color: var(--ks-text-link);
            font-weight: var(--ks-font-weight-semibold);
            background-color: var(--ks-bg-hover);
        }
    }
</style>