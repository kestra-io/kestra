<template>
    <div class="docsMenuWrapper">
        <el-button @click="menuOpen = !menuOpen" class="menuOpener">
            {{ $t("documentationMenu") }} <MenuDown class="expandIcon" />
        </el-button>
        <ul v-if="menuOpen" class="docsMenu list-unstyled d-flex flex-column gap-3">
            <template v-if="rawStructure">
                <li v-for="[sectionName, children] in sectionsWithChildren" :key="sectionName" :class="{'active-section': isCurrentSection(sectionName)}">
                    <span class="text-secondary">
                        {{ sectionName.toUpperCase() }}
                    </span>
                    <RecursiveToc :parent="{children}">
                        <template #default="{path, title}">
                            <ContextDocsLink
                                :href="path.slice(5)"
                                useRaw
                                :class="{'active-page': isCurrentPage(path)}"
                                @click="menuOpen = false"
                            >
                                {{ title.capitalize() }}
                            </ContextDocsLink>
                        </template>
                    </RecursiveToc>
                </li>
            </template>
            <li v-else>
                Loading Menu...
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {useDocStore} from "../../stores/doc";

    import MenuDown from "vue-material-design-icons/MenuDown.vue";

    import RecursiveToc from "./RecursiveToc.vue";
    import ContextDocsLink from "./ContextDocsLink.vue";

    const docStore = useDocStore();

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
            "Multi-Language Script Tasks",
            "Version Control & CI/CD",
            "Plugin Developer Guide",
            "How-to Guides"
        ],
        "Scale with Kestra": [
            "Cloud & Enterprise Edition",
            "Task Runners",
            "Best Practices"
        ],
        "Manage Kestra": [
            "Administrator Guide",
            "Migration Guide"
        ],
        "Reference Docs": [
            "Configuration",
            "Expressions",
            "API Reference",
            "Terraform Provider",
        ]
    }

    const rawStructure = ref<Record<string, any> | undefined>();
    const currentDocPath = computed(() => docStore.docPath);

    const normalizePath = (path: string) => {
        if (!path) return "";
        return path.replace(/^docs\//, "").replace(/^\/+|\/+$/g, "");
    };

    const isCurrentPage = (path: string) => {
        if (!currentDocPath.value || !path) return false;
        const normalizedCurrent = normalizePath(currentDocPath.value);
        const normalizedPath = normalizePath(path);

        if (normalizedCurrent === normalizedPath) return true;

        if (normalizedCurrent.startsWith(normalizedPath + "/")) return true;

        return false;
    };

    const isCurrentSection = (sectionName: string) => {
        if (!currentDocPath.value) return false;
        const sectionChildren = sectionsWithChildren.value?.find(([name]) => name === sectionName)?.[1] || [];
        return sectionChildren.some((child: { path: string }) => isCurrentPage(child.path));
    };

    watch(menuOpen, async (val) => {
        if(!val || rawStructure.value !== undefined) return;
        rawStructure.value = await docStore.children();
    });

    const toc = computed<{title: string}[]>(() => {
        if (rawStructure.value === undefined) {
            return undefined;
        }

        const childrenWithMetadata = Object.entries(rawStructure.value)
            .reduce((acc: Record<string, any>, [url, metadata]) => {
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

        return Object.entries(SECTIONS).map(([section, childrenTitles]) => [section, toc.value.filter(({title}) => childrenTitles.includes(title))] as [string, {title: string, path: string}[]]);
    });
</script>

<style scoped lang="scss">
    ul > li > span:first-child {
        font-size: 12px;
    }

    .docsMenu{
        position: absolute;
        z-index: 1000;
        padding: .5rem;
        left: 26px;
        top: 100%;
        right: 26px;
        background-color: var(--ks-background-card);
        border-radius: 6px;

        a {
            color: var(--ks-content-primary);
            text-decoration: none;
            display: block;
            padding: .25rem .5rem;
            border-radius: 4px;

            &:hover {
                color: var(--ks-primary);
                background-color: var(--ks-select-hover);
            }

            &.active-page {
                color: var(--ks-content-link);
                font-weight: 600;
            }
        }

        li {
            > span {
                display: block;
                padding: .25rem .5rem;
                margin-bottom: .25rem;
                border-radius: 4px;
            }

            &.active-section {
                > span {
                    color: var(--ks-content-link);
                    font-weight: 600;
                }
            }

            &:hover {
                > span {
                    background-color: var(--ks-select-hover);
                }
            }
        }
    }

    .docsMenuWrapper{
        position: relative;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        padding-left: 27px;
        padding-right: 27px;
    }

    .menuOpener{
        flex: 1;
        margin: 0;
        width: 100%;
    }

    .expandIcon{
        margin-left: 1rem;
    }
</style>