<template>
    <el-autocomplete
        ref="search"
        class="flex-shrink-0"
        v-model="query"
        :fetch-suggestions="search"
        popper-class="doc-toc-search-popper"
        :placeholder="$t('search')"
    >
        <template #prefix>
            <magnify />
        </template>
        <template #default="{item}">
            <router-link
                :to="{path: '/' + item.parsedUrl}"
                class="d-flex gap-2"
            >
                {{ item.title }}
                <arrow-right class="is-justify-end" />
            </router-link>
        </template>
    </el-autocomplete>
    <ul class="list-unstyled d-flex flex-column gap-3">
        <li v-for="[sectionName, children] in sectionsWithChildren" :key="sectionName">
            <span class="text-secondary">
                {{ sectionName.toUpperCase() }}
            </span>
            <recursive-toc :parent="{children}" />
        </li>
    </ul>
</template>

<script setup>
    import RecursiveToc from "./RecursiveToc.vue";
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
</script>

<script>
    export default {
        data() {
            return {
                sections: {
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
                },
                rawStructure: undefined,
                query: undefined
            }
        },
        computed: {
            toc() {
                if (this.rawStructure === undefined) {
                    return undefined;
                }

                let childrenWithMetadata = JSON.parse(JSON.stringify(this.rawStructure));
                childrenWithMetadata = Object.fromEntries(Object.entries(childrenWithMetadata)
                    .filter(([_, {hideSidebar}]) => !hideSidebar)
                    .map(([url, metadata]) => [url, {
                        ...metadata,
                        path: url
                    }]));
                Object.entries(childrenWithMetadata)
                    .forEach(([url, metadata]) => {
                        const split = url.split("/");
                        const parentUrl = split.slice(0, split.length - 1).join("/");
                        const parent = childrenWithMetadata[parentUrl];
                        if (parent !== undefined) {
                            parent.children = [...(parent.children ?? []), metadata];
                        }
                    });

                return Object.entries(childrenWithMetadata)[0]?.[1]?.children;
            },
            sectionsWithChildren() {
                if (this.toc === undefined) {
                    return undefined;
                }

                return Object.entries(this.sections).map(([section, childrenTitles]) => [section, this.toc.filter(({title}) => childrenTitles.includes(title))]);
            }
        },
        async mounted() {
            this.rawStructure = await this.$store.dispatch("doc/children");
        },
        methods: {
            async search(query, cb) {
                cb(await this.$store.dispatch("doc/search", query));
            }
        }
    };
</script>

<style lang="scss" scoped>
    ul > li > span:first-child {
        font-size: 12px;
    }
</style>