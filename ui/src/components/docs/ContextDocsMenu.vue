<template>
    <div class="docsMenuWrapper">
        <el-button @click="menuOpen = !menuOpen" class="menuOpener">
            {{ t("documentationMenu") }} <MenuDown class="expandIcon" />
        </el-button>
        <ul v-if="menuOpen" class="docsMenu list-unstyled d-flex flex-column gap-3">
            <template v-if="rawStructure">
                <li v-for="[sectionName, children] in sectionsWithChildren" :key="sectionName">
                    <span class="text-secondary">
                        {{ sectionName.toUpperCase() }}
                    </span>
                    <recursive-toc :parent="{children}">
                        <template #default="{path, title}">
                            <context-docs-link @click="menuOpen = false" :href="path.slice(5)" use-raw>
                                {{ title.capitalize() }}
                            </context-docs-link>
                        </template>
                    </recursive-toc>
                </li>
            </template>
            <li v-else>
                Loading Menu...
            </li>
        </ul>
    </div>
</template>

<script setup>
    import {ref, computed, watch} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    const {t} = useI18n({useScope: "global"});

    import MenuDown from "vue-material-design-icons/MenuDown.vue";

    import RecursiveToc from "./RecursiveToc.vue";
    import ContextDocsLink from "./ContextDocsLink.vue";

    const store = useStore();

    const menuOpen = ref(false);

    const SECTIONS = {
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
    }

    const rawStructure = ref(undefined);

    watch(menuOpen, async (val) => {
        if(!val || rawStructure.value !== undefined){
            return;
        }
        rawStructure.value = await store.dispatch("doc/children");
    });

    const toc = computed(() => {
        if (rawStructure.value === undefined) {
            return undefined;
        }

        const childrenWithMetadata = Object.entries(rawStructure.value)
            .reduce((acc, [url, metadata]) => {
                if(!metadata || metadata.hideSidebar){
                    return acc;
                }

                acc[url] = {
                    ...metadata,
                    path: url
                };

                return acc
            }, {});

        for(const url in childrenWithMetadata){
            const metadata = childrenWithMetadata[url];
            const split = url.split("/");
            const parentUrl = split.slice(0, split.length - 1).join("/");
            const parent = childrenWithMetadata[parentUrl];
            if (parent !== undefined) {
                parent.children = [...(parent.children ?? []), metadata];
            }
        }

        return Object.entries(childrenWithMetadata)[0]?.[1]?.children;
    })

    const sectionsWithChildren = computed(() => {
        if (toc.value === undefined) {
            return undefined;
        }

        return Object.entries(SECTIONS).map(([section, childrenTitles]) => [section, toc.value.filter(({title}) => childrenTitles.includes(title))]);
    });
</script>

<style lang="scss" scoped>
    ul > li > span:first-child {
        font-size: 12px;
    }

    .docsMenu{
        position: absolute;
        z-index: 1000;
        padding: .5rem 1rem;
        left: 1rem;
        top: 100%;
        right: 1rem;
        background-color: var(--ks-background-card);
        border-radius: 6px;
    }

    .docsMenuWrapper{
        position: relative;
        display: flex;
    }

    .menuOpener{
        flex: 1;
        margin: 1rem;
    }

    .expandIcon{
        margin-left: 1rem;
    }
</style>