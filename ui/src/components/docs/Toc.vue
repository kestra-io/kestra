<template>
    <el-autocomplete
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
            <router-link
                :to="{path: '/' + item.parsedUrl}"
                class="d-flex gap-2"
            >
                {{ item.title }}
                <ArrowRight class="is-justify-end" />
            </router-link>
        </template>
    </el-autocomplete>
    <ul class="list-unstyled d-flex flex-column gap-3">
        <li v-for="[sectionName, children] in sectionsWithChildren" :key="sectionName">
            <span class="text-secondary">
                {{ sectionName.toUpperCase() }}
            </span>
            <RecursiveToc :parent="{children}" />
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted} from "vue";
    import {useDocStore} from "../../stores/doc";
    import RecursiveToc from "./RecursiveToc.vue";
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    interface TocItem {
        title: string;
        path: string;
        hideSidebar?: boolean;
        children?: TocItem[];
    }

    interface SearchResult {
        parsedUrl: string;
        title: string;
    }

    const docStore = useDocStore();

    const sections = {
        "Get Started with Kestra": [
            "Getting Started",
            "Tutorial",
            "Architecture",
            "Installation Guide",
            "User Interface"
        ],
        "Build with Kestra": [
            "Concepts",
            "Workflow Components",
            "Expressions",
            "Version Control & CI/CD",
            "Plugin Developer Guide",
            "How-to Guides"
        ],
        "Scale with Kestra": [
            "Enterprise Edition",
            "Task Runners",
            "Best Practices"
        ],
        "Manage Kestra": [
            "Administrator Guide",
            "Configuration Guide",
            "Migration Guide",
            "Terraform Provider",
            "API Reference"
        ]
    };

    const rawStructure = ref<any>(undefined);
    const query = ref<string>("");

    const toc = computed((): TocItem[] | undefined => {
        if (rawStructure.value === undefined) {
            return undefined;
        }

        let childrenWithMetadata: Record<string, TocItem> = Object.fromEntries(Object.entries(rawStructure.value)
            .filter(([_, metadata]: [string, any]) => !metadata.hideSidebar)
            .map(([url, metadata]: [string, any]) => [url, {
                ...metadata,
                path: url
            } as TocItem]));
        Object.entries(childrenWithMetadata)
            .forEach(([url, metadata]: [string, any]) => {
                const split = url.split("/");
                const parentUrl = split.slice(0, split.length - 1).join("/");
                const parent = childrenWithMetadata[parentUrl];
                if (parent !== undefined) {
                    parent.children = [...(parent.children ?? []), metadata];
                }
            });

        return Object.entries(childrenWithMetadata)[0]?.[1]?.children;
    });

    const sectionsWithChildren = computed((): [string, TocItem[]][] | undefined => {
        if (toc.value === undefined) {
            return undefined;
        }

        return Object.entries(sections).map(([section, childrenTitles]) => [
            section, 
            toc.value!.filter(({title}) => childrenTitles.includes(title))
        ]);
    });

    onMounted(async () => {
        rawStructure.value = await docStore.children();
    });

    const search = async (query: string, cb: (results: SearchResult[]) => void) => {
        cb(await docStore.search({q: query}));
    };
</script>

<style lang="scss" scoped>
    ul > li > span:first-child {
        font-size: 12px;
    }
</style>