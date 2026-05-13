<template>
    <div class="d-flex flex-column gap-4">
        <!-- Root plugin page with subgroups -->
        <template v-if="subGroup === undefined && plugins.length > 1">
            <div class="pb-2">
                <div class="row g-4 last">
                    <div
                        class="col-md-6"
                        v-for="subGroupWrapper in subGroupsWrappers"
                        :key="subGroupName(subGroupWrapper)"
                    >
                        <KsSubgroupCard
                            :id="slugifyPlugin(subGroupName(subGroupWrapper))"
                            :iconSrc="icons[subGroupWrapper.subGroup ?? ''] ?? icons[subGroupWrapper.group]"
                            :text="getSubgroupTitle(subGroupWrapper)"
                            :description="getSubgroupDescription(subGroupWrapper)"
                            :href="`${routePath}/${slugifyPlugin(subGroupName(subGroupWrapper))}`"
                            :routePath="routePath"
                            :totalCount="getTotalElementCount(subGroupWrapper)"
                            :blueprintsCount="subgroupBlueprintCounts?.[`${slugifyPlugin(subGroupWrapper.group ?? subGroupWrapper.name)}-${slugifyPlugin(subGroupName(subGroupWrapper))}`] ?? 0"
                            :isActive="activeId?.toLowerCase() === slugifyPlugin(subGroupName(subGroupWrapper))"
                            class="text-capitalize h-100"
                            @navigate="emit('navigate', $event)"
                        />
                    </div>
                </div>
            </div>
        </template>
        <template v-else-if="plugin">
            <div class="d-flex flex-column elements-section pb-3" v-for="(elements, elementType) in elementsByType" :key="elementType">
                <h2 :id="`section-${slugifyPlugin(elementType as string)}`" class="text-capitalize">
                    {{ elementType === 'additional Plugins' ? 'Tasks' : elementType }}
                </h2>
                <div class="row g-4 last">
                    <div class="col-md-6" v-for="element in elements" :key="element">
                        <KsElementCard
                            :id="slugifyPlugin(element)"
                            :text="elementName(element)"
                            :pluginClass="element"
                            :href="elementHref(element)"
                            :routePath="routePath"
                            :title="schemas?.[element]?.title"
                            class="h-100"
                            @navigate="emit('navigate', $event)"
                        >
                            <template #markdown="{content}">
                                <slot name="markdown" :content="content" />
                            </template>
                        </KsElementCard>
                    </div>
                </div>
            </div>
        </template>
        <!--
            The description section intentionally has no hardcoded heading.
            Plugin authors control the heading via their doc markdown file
            (e.g. src/main/resources/doc/io.kestra.plugin.*.md).
            The id is kept on the wrapper so TOC anchor links still resolve.
        -->
        <template v-if="description !== undefined && plugin?.longDescription">
            <div id="how-to-use-this-plugin" class="description">
                <div ref="contentWrap" class="markdown-container" :class="{expanded: isExpanded}">
                    <div ref="contentInner" class="markdown-inner">
                        <slot name="markdown" :content="description.replace(/ *:(?![ /])/g, ': ')" />
                    </div>
                    <div v-if="isOverflow && !isExpanded" class="gradient-overlay" />
                </div>
                <div v-if="isOverflow || isExpanded" class="more-wrap text-center">
                    <button class="more-btn" @click="toggleExpand">
                        {{ isExpanded ? "See less" : "See more" }}
                        <KsIcon :name="isExpanded ? 'chevron-up' : 'chevron-down'" />
                    </button>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useElementSize} from "@vueuse/core"
    import type {Plugin, PluginMetadata} from "../../utils/plugins"
    import {subGroupName, extractPluginElements, slugifyPlugin} from "../../utils/plugins"
    import {usePluginElementCounts} from "../../composables/usePluginElementCounts"
    import KsIcon from "../Basic/KsIcon.vue"
    import KsElementCard from "./KsElementCard.vue"
    import KsSubgroupCard from "./KsSubgroupCard.vue"

    defineOptions({name: "KsPluginIndex"})

    const props = defineProps<{
        plugins: Plugin[];
        pluginName: string;
        routePath: string;
        icons: Record<string, string>;
        subGroup?: string;
        activeId?: string;
        subgroupBlueprintCounts?: Record<string, number>;
        metadataMap?: Record<string, PluginMetadata>;
        schemas?: Record<string, {title?: string}>;
    }>()

    const emit = defineEmits<{(e: "navigate", url: string): void}>()

    const getSubgroupMetadata = (subGroupWrapper: Plugin) =>
        props.metadataMap?.[subGroupWrapper.subGroup ?? subGroupWrapper.group]

    const getSubgroupDescription = (subGroupWrapper: Plugin) =>
        getSubgroupMetadata(subGroupWrapper)?.description ?? subGroupWrapper.description

    const getSubgroupTitle = (subGroupWrapper: Plugin) =>
        getSubgroupMetadata(subGroupWrapper)?.title ?? subGroupWrapper.title ?? subGroupName(subGroupWrapper)

    const plugin = computed(() =>
        props.plugins.find(p =>
            props.subGroup === undefined ? true : (slugifyPlugin(subGroupName(p)) === props.subGroup),
        ),
    )

    const description = computed(() => plugin.value?.longDescription ?? plugin.value?.description)

    const subGroupsWrappers = computed(() =>
        props.plugins.filter(p =>
            p.name.toLowerCase() === props.pluginName.toLowerCase() && p.subGroup !== undefined,
        ) as (Plugin & {subGroup: string})[],
    )

    const elementName = (qualifiedName: string) => {
        const split = qualifiedName.split(".")
        return split[split.length - 1]
    }

    const elementHref = (element: string) => `${props.routePath}/${element.toLowerCase()}`

    const getTotalElementCount = (p: Plugin): number =>
        Object.values(extractPluginElements(p)).reduce((sum, arr) => sum + arr.length, 0)

    const {elementsByType} = usePluginElementCounts(plugin)

    const contentWrap = ref<HTMLElement | null>(null)
    const contentInner = ref<HTMLElement | null>(null)
    const isExpanded = ref(false)

    const {height: wrapHeight} = useElementSize(contentWrap)
    const {height: innerHeight} = useElementSize(contentInner)

    const isOverflow = computed(() => innerHeight.value > wrapHeight.value + 2)

    const toggleExpand = () => {isExpanded.value = !isExpanded.value}
</script>

<style lang="scss" scoped>
    h2 {
        margin-top: 0;
    }

    .description {
        border-top: 1px solid var(--ks-border-secondary);
        padding: 2rem 3rem;
        margin: 0 -3rem;
    }

    .row > * {
        padding-inline: 8px;
        margin-top: 1rem;
    }

    .markdown-container {
        position: relative;
        max-height: 384px;
        overflow: hidden;
        transition: max-height 250ms ease-in-out;

        &.expanded {
            max-height: none;
        }
    }

    .markdown-inner {
        color: var(--ks-content-primary);
    }

    .gradient-overlay {
        pointer-events: none;
        position: absolute;
        inset: auto 0 0;
        height: 140px;
        background: linear-gradient(transparent, var(--ks-background-primary));
    }

    .more-btn {
        background: var(--ks-background-primary);
        color: var(--ks-content-primary);
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 999px;
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        cursor: pointer;
        font-weight: 400;
    }
</style>
