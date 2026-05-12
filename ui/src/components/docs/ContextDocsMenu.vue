<template>
    <div class="docsMenuWrapper">
        <KsButton
            @click="menuOpen = !menuOpen"
            class="menuOpener"
            :class="{'is-open': menuOpen}"
        >
            {{ $t("documentationMenu") }}
            <ChevronDown
                class="expandIcon"
                :class="{'rotate-icon': menuOpen}"
            />
        </KsButton>
        <div v-if="menuOpen" class="docsMenuContainer">
            <ul class="docsMenu list-unstyled d-flex flex-column m-0">
                <template v-if="rawStructure">
                    <li
                        v-for="{section, children} in sectionsWithChildren"
                        :key="section"
                        :class="{'active-section': isCurrentSection(section)}"
                    >
                        <span class="text-secondary">
                            {{ section.toUpperCase() }}
                        </span>
                        <RecursiveToc :parent="{children: children ?? []}">
                            <template #default="{path, sidebarTitle, title, class: childClass}">
                                <ContextDocsLink
                                    :href="path"
                                    useRaw
                                    :class="[{'active-page': isCurrentPage(path)}, childClass]"
                                    @click="menuOpen = false"
                                >
                                    {{ (sidebarTitle ?? title).capitalize() }}
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
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useDocStore} from "../../stores/doc"
    import {SECTIONS} from "./docsUtils"

    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    import RecursiveToc from "./RecursiveToc.vue"
    import ContextDocsLink from "./ContextDocsLink.vue"

    const docStore = useDocStore()

    const menuOpen = ref(false)

    const rawStructure = ref<Record<string, any> | undefined>()
    const currentDocPath = computed(() => docStore.docPath)

    const normalizePath = (path: string) => {
        if (!path) return ""
        return path.replace(/^docs\//, "").replace(/^\/+|\/+$/g, "")
    }

    const isCurrentPage = (path: string) => {
        if (!currentDocPath.value || !path) return false
        const normalizedCurrent = normalizePath(currentDocPath.value)
        const normalizedPath = normalizePath(path)

        if (normalizedCurrent === normalizedPath) return true

        if (normalizedCurrent.startsWith(normalizedPath + "/")) return true

        return false
    }

    const isCurrentSection = (sectionName: string) => {
        if (!currentDocPath.value) return false
        const sectionChildren = sectionsWithChildren.value?.find(({section}) => section === sectionName)?.children || []
        return sectionChildren.some(child => isCurrentPage(child.path))
    }

    watch(menuOpen, async (val) => {
        if(!val || rawStructure.value !== undefined) return
        rawStructure.value = await docStore.children()
    })

    const toc = computed(() => {
        if (rawStructure.value === undefined) {
            return undefined
        }

        const childrenWithMetadata = Object.entries(rawStructure.value)
            .filter(([p]) => p.startsWith("docs/") && !p.endsWith(".png") && !p.endsWith(".svg"))
            .reduce((acc: Record<string, any>, [url, metadata]) => {
                if(!metadata || metadata.hideSidebar){
                    return acc
                }

                const cleanUrl = url.replace(/\/index\.mdx?$/, "").replace(/\.mdx?$/, "")

                acc[cleanUrl] = {
                    ...metadata,
                    path: cleanUrl,
                }

                return acc
            }, {})

        for(const url in childrenWithMetadata){
            const metadata = childrenWithMetadata[url]
            const split = url.split("/")
            const parentUrl = split.slice(0, split.length - 1).join("/")
            const parent = childrenWithMetadata[parentUrl]
            if (parent !== undefined) {
                parent.children = [...(parent.children ?? []), metadata]
            }
        }

        return Object.values(childrenWithMetadata) as {path: string, title: string, sidebarTitle: string, children: any[]}[]
    })

    const sectionsWithChildren = computed(() => Object.entries(SECTIONS)
        .map(([section, childrenTitles]) =>({

            section,
            children: childrenTitles
                .map(name => toc.value?.find(({title, sidebarTitle, path}) =>
                    path.split("/").length === 2 && (sidebarTitle === name || title === name),
                ))
                .filter((item): item is NonNullable<typeof item> => !!item),
        })),
    )
</script>

<style scoped lang="scss">
    ul > li > span:first-child {
        font-size: var(--ks-font-size-xs);
    }

    $scrollbar-width: 6px;
    $link-radius: 6px;
    $transition-timing: cubic-bezier(0.16, 1, 0.3, 1);

    @mixin custom-scrollbar {
        &::-webkit-scrollbar {
            width: $scrollbar-width;
        }
        &::-webkit-scrollbar-track {
            background: transparent;
            border-radius: $link-radius;
        }
        &::-webkit-scrollbar-thumb {
            background-color: transparent;
            border-radius: $link-radius;
        }
        &:hover::-webkit-scrollbar-thumb {
            background-color: var(--ks-border-default);
        }
    }

    .docsMenuWrapper {
        position: sticky;
        top: 1rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        padding: 0 27px;
        z-index: 3;
    }

    .menuOpener {
        flex: 1;
        margin: 0;
        width: 100%;
        border-radius: $link-radius;
        font-weight: 600;
        transition: all 0.2s ease;

        &.is-open {
            border-bottom-left-radius: 0;
            border-bottom-right-radius: 0;
            position: relative;
            z-index: 1001;
        }
    }

    .expandIcon {
        margin-left: 1rem;
        font-size: 1.5rem;
        transition: transform 0.2s $transition-timing;
        &.rotate-icon {
            transform: rotate(180deg);
        }
    }

    .docsMenuContainer {
        position: absolute;
        z-index: 1000;
        padding: 1rem 0.25rem 1rem 0.5rem;
        left: 27px;
        right: 27px;
        top: 100%;
        background-color: var(--ks-bg-surface);
        border-radius: 0 0 $link-radius $link-radius;
        border: 1px solid var(--ks-border-default);
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
        margin-top: -1px;
    }

    .docsMenu {
        list-style: none;
        padding-left: 0;
        max-height: calc(100vh - 210px);
        overflow-y: auto;
        padding-right: 0.25rem;

        @include custom-scrollbar;

        a, :deep(span[class*="depth-"]) {
            color: var(--ks-text-primary);
            text-decoration: none;
            display: block;
            padding: 0.25rem 0.5rem;
            border-radius: $link-radius;
            transition: all 0.2s ease;
            margin-bottom: 2px;
            font-size: 0.85rem;
            cursor: pointer;
            width: 100%;

            @for $i from 0 through 5 {
                $base-pad: 0.5rem + ($i * 1rem);

                &.depth-#{$i} {
                    padding-left: $base-pad;
                    @if $i == 0 {
                        font-weight: 500;
                    } @else if $i == 1 {
                        font-size: 0.8rem;
                        color: var(--ks-text-secondary);
                    } @else {
                        font-size: var(--ks-font-size-xs);
                        color: var(--ks-text-secondary);
                        opacity: max(0.6, 0.9 - ($i - 2) * 0.1);
                    }
                }

                &.active-page.depth-#{$i} {
                    padding-left: calc(#{$base-pad} - 3px);
                }
            }

            &:hover {
                color: var(--ks-primary);
                background-color: var(--ks-btn-secondary-bg-hover);
            }

            &.active-page {
                color: var(--ks-text-link) !important;
                font-weight: 600;
                opacity: 1 !important;
                background-color: var(--ks-btn-secondary-bg-hover);
            }
        }

        li {
            margin-bottom: 0.5rem;

            &:last-child {
                margin-bottom: 0;
            }

            > span {
                display: block;
                padding: 0.25rem 0.5rem;
                margin-bottom: 0.15rem;
                font-size: 11px;
                font-weight: 700;
                letter-spacing: 0.05em;
                color: var(--ks-text-secondary);
                text-transform: uppercase;
                border-radius: $link-radius;
            }

            &.active-section {
                > span {
                    color: var(--ks-text-link);
                }
            }
        }
    }
</style>
