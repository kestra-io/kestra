<template>
    <TopNavBar :title="title" />

    <KsAlert v-if="loadError" type="error">
        {{ $t("pluginPage.loadError") }}
    </KsAlert>

    <section v-else-if="!filteredPlugins" class="plugins-container plugins-container--loading">
        <KsSkeleton v-for="n in 8" :key="n" animated :rows="4" class="plugin-skeleton" />
    </section>

    <template v-else>
        <div class="filter-toolbar">
            <div class="filter-toolbar__search">
                <KsSearch
                    v-model="searchText"
                    :placeholder="$t('pluginPage.search')"
                    clearable
                />
            </div>

            <div class="category-tags">
                <KsCheckTag
                    v-for="category in availableCategories"
                    :key="category"
                    pill
                    :checked="selectedCategories.includes(category)"
                    @change="toggleCategory(category)"
                >
                    <template #icon>
                        <component :is="CATEGORY_ICONS[category] ?? TagOutline" :size="16" />
                    </template>
                    <span class="category-label">{{ category }}</span>
                </KsCheckTag>
            </div>

            <div class="sort-by">
                <span class="sort-label">{{ $t("pluginPage.sortBy") }}</span>
                <KsSelect v-model="sortBy" size="small" class="sort-select">
                    <KsOption
                        v-for="option in sortOptions"
                        :key="option.value"
                        :value="option.value"
                        :label="option.label"
                    />
                </KsSelect>
            </div>
        </div>

        <KsEmpty
            v-if="pluginsList.length === 0"
            :description="$t('pluginPage.noResults')"
            class="my-6"
        />

        <section v-else class="plugins-container">
            <KsTooltip
                v-for="plugin in pluginsList"
                :showAfter="1000"
                :key="`${plugin.name}-${plugin.subGroup ?? ''}`"
            >
                <template #content>
                    <div class="tasks-tooltips">
                        <template
                            v-for="([elementType, elements]) in allElementsByTypeEntries(plugin)"
                            :key="elementType"
                        >
                            <p
                                v-if="elements.length > 0"
                                class="mb-0"
                            >
                                {{ $t(elementType) }}
                            </p>
                            <ul>
                                <li
                                    v-for="element in elements"
                                    :key="element"
                                >
                                    {{ element }}
                                </li>
                            </ul>
                        </template>
                    </div>
                </template>
                <KsPluginCard
                    :iconCls="hasIcon(plugin.subGroup) ? plugin.subGroup : plugin.group"
                    :icons
                    :title="plugin.title.capitalize()"
                    :description="plugin.description"
                    :categories="plugin.categories"
                    :taskCount="taskCount(plugin)"
                    :blueprintCount="blueprintCount(plugin)"
                    @click="openGroup(plugin)"
                />
            </KsTooltip>
        </section>
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, markRaw, onMounted, watch, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {KsPluginCard, KsSearch, KsAlert, KsEmpty, KsSkeleton} from "@kestra-io/design-system"
    import {isEntryAPluginElementPredicate, isPluginMatched, type Plugin, type PluginElement} from "../../utils/pluginUtils"
    import {usePluginsStore} from "../../stores/plugins"
    import {usePluginsEnrichmentStore} from "../../stores/pluginsEnrichment"
    import {useMiscStore} from "override/stores/misc"
    import useRouteContext from "../../composables/useRouteContext"
    import TopNavBar from "../../components/layout/TopNavBar.vue"

    import BriefcaseOutline from "vue-material-design-icons/BriefcaseOutline.vue"
    import CloudOutline from "vue-material-design-icons/CloudOutline.vue"
    import CogOutline from "vue-material-design-icons/CogOutline.vue"
    import Creation from "vue-material-design-icons/Creation.vue"
    import SourceBranch from "vue-material-design-icons/SourceBranch.vue"
    import TagOutline from "vue-material-design-icons/TagOutline.vue"

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()
    const pluginsStore = usePluginsStore()
    const miscStore = useMiscStore()
    const enrichmentStore = usePluginsEnrichmentStore()

    const title = computed(() => t("plugins.names"))
    const routeInfo = computed(() => ({title: title.value, breadcrumb: undefined}))
    useRouteContext(routeInfo)

    const filteredPlugins = ref<Plugin[] | undefined>(undefined)
    const loadError = ref(false)

    const icons = computed(() => ({
        ...pluginsStore.icons,
        ...pluginsStore.groupIcons,
    }))
    const searchText = ref("")
    const selectedCategories = ref<string[]>([])

    type SortKey = "nameAsc" | "nameDesc" | "newest" | "mostUsed"
    const sortBy = ref<SortKey>("nameAsc")

    const sortOptions = computed<{value: SortKey, label: string}[]>(() => [
        {value: "nameAsc", label: t("pluginPage.sort.nameAsc")},
        {value: "nameDesc", label: t("pluginPage.sort.nameDesc")},
        {value: "newest", label: t("pluginPage.sort.newest")},
        {value: "mostUsed", label: t("pluginPage.sort.mostUsed")},
    ])

    const searchInput = computed(() => searchText.value.toLowerCase())

    const pluginTitle = (plugin: Plugin): string => String(plugin?.manifest?.["X-Kestra-Title"] ?? plugin?.title ?? "").toLowerCase()

    const baseList = computed<Plugin[]>(() => {
        const source = filteredPlugins.value ?? []
        const grouped = source.reduce((acc: Record<string, Plugin[]>, plugin) => {
            (acc[plugin.group] ??= []).push(plugin)
            return acc
        }, {})

        const filtered = Object.values(grouped).flatMap(group =>
            group.filter(p => p.subGroup).length ? group.filter(p => p.subGroup) : group.filter(p => !p.subGroup),
        )

        return filtered
            .filter((plugin, index, self) =>
                index === self.findIndex(p => p.name === plugin.name && p.subGroup === plugin.subGroup),
            )
            .filter(plugin => isVisible(plugin))
    })

    const CATEGORY_ICONS: Record<string, Component> = markRaw({
        AI: Creation,
        BUSINESS: BriefcaseOutline,
        CLOUD: CloudOutline,
        CORE: Creation,
        DATA: SourceBranch,
        INFRASTRUCTURE: CogOutline,
    })

    const availableCategories = computed<string[]>(() =>
        [...new Set((filteredPlugins.value ?? []).flatMap(p => p.categories ?? []))].sort(),
    )

    const toggleCategory = (category: string) => {
        const idx = selectedCategories.value.indexOf(category)
        if (idx === -1) {
            selectedCategories.value.push(category)
        } else {
            selectedCategories.value.splice(idx, 1)
        }
    }

    const matchesSelectedCategories = (plugin: Plugin): boolean => {
        if (selectedCategories.value.length === 0) {
            return true
        }
        const cats: string[] = plugin?.categories ?? []
        return selectedCategories.value.some(selected => cats.includes(selected))
    }

    // Comparator map keyed by sortBy. `newest` / `mostUsed` rely on enrichment data
    // (api.kestra.io); missing values degrade gracefully to the name comparison.
    const nameAsc = (a: Plugin, b: Plugin): number => pluginTitle(a).localeCompare(pluginTitle(b))

    const newestComparator = (a: Plugin, b: Plugin): number => {
        const dateA = enrichmentStore.getEnrichment(a)?.lastReleasedAt
        const dateB = enrichmentStore.getEnrichment(b)?.lastReleasedAt
        const tA = dateA ? new Date(dateA).getTime() : 0
        const tB = dateB ? new Date(dateB).getTime() : 0
        if (!tA && !tB) return nameAsc(a, b)
        if (!tA) return 1
        if (!tB) return -1
        return tB - tA || nameAsc(a, b)
    }

    const mostUsedComparator = (a: Plugin, b: Plugin): number => {
        const uA = enrichmentStore.getEnrichment(a)?.usageCount ?? 0
        const uB = enrichmentStore.getEnrichment(b)?.usageCount ?? 0
        return uB - uA || nameAsc(a, b)
    }

    const comparators: Record<SortKey, (a: Plugin, b: Plugin) => number> = {
        nameAsc,
        nameDesc: (a, b) => nameAsc(b, a),
        newest: newestComparator,
        mostUsed: mostUsedComparator,
    }

    const pluginsList = computed<Plugin[]>(() => {
        return baseList.value
            .filter(plugin => isPluginMatched(plugin, searchInput.value))
            .filter(plugin => matchesSelectedCategories(plugin))
            .slice()
            .sort(comparators[sortBy.value] ?? nameAsc)
    })

    const loadPluginIcons = async () => {
        try {
            await pluginsStore.ensureGroupIcons()
        } catch (error) {
            console.error("Failed to load plugin icons:", error)
        }
    }

    const openGroup = (plugin: Plugin) => {
        if (!plugin?.name) return
        const params: Record<string, string> = {...route.params as Record<string, string>, name: plugin.name}
        if (plugin.subGroup) params.subGroup = plugin.subGroup
        router.push({name: "plugins/group", params})
    }

    const isVisible = (plugin: Plugin) => {
        return allElements(plugin).length > 0
    }

    const hasIcon = (cls: string | undefined) => {
        return cls !== undefined && icons.value[cls] !== undefined
    }

    const allElementsByTypeEntries = (plugin: Plugin): [string, string[]][] => {
        return Object.entries(plugin)
            .filter((entry): entry is [string, PluginElement[]] => isEntryAPluginElementPredicate(entry[0], entry[1]))
            .map(([elementType, elements]) => [
                elementType,
                elements.filter(({deprecated}) => !deprecated).map(({cls}) => cls),
            ])
    }

    const allElements = (plugin: Plugin): string[] => {
        return allElementsByTypeEntries(plugin).flatMap(([, classes]) => classes)
    }

    const taskCount = (plugin: Plugin): number => allElements(plugin).length

    const blueprintCount = (plugin: Plugin): number => {
        return enrichmentStore.getEnrichment(plugin)?.blueprintCount ?? 0
    }

    watch(
        () => pluginsStore.plugins,
        async () => {
            filteredPlugins.value = await pluginsStore.filteredPlugins([
                "apps",
                "appBlocks",
                "charts",
                "dataFilters",
                "dataFiltersKPI",
            ])
        },
        {immediate: true},
    )

    onMounted(() => {
        loadPluginIcons()
        miscStore.loadConfigs()
        pluginsStore.ensurePlugins().catch((err) => {
            console.error("Failed to load plugins", err)
            loadError.value = true
        })
        enrichmentStore.fetchEnrichment()
    })
</script>

<style scoped lang="scss">
    
    .filter-toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
        width: 100%;
        padding: var(--ks-spacing-6);

        &__search {
            min-width: 17rem;
        }

        .category-tags {
            display: flex;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2);
            flex: 1 1 auto;
            min-width: 0;
        }

        .category-label {
            display: inline-block;
            text-transform: lowercase;

            &::first-letter {
                text-transform: uppercase;
            }
        }

        .sort-by {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            flex: 0 0 auto;
        }

        .sort-label {
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
            white-space: nowrap;
        }

        .sort-select {
            width: 8.75rem;
        }
    }

    .plugins-container {
        display: grid;
        gap: var(--ks-spacing-4);
        grid-template-columns: repeat(auto-fill, minmax(17.5rem, 1fr));
        padding: 0 var(--ks-spacing-6) var(--ks-spacing-10) var(--ks-spacing-6);

        &--loading {
            margin-top: var(--ks-spacing-4);
        }
    }

    .plugin-skeleton {
        padding: var(--ks-spacing-4);
        background-color: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
    }

    .tasks-tooltips {
        max-height: 20rem;
        overflow-y: auto;
        overflow-x: hidden;
    }

    @media (max-width: 650px) {
        .plugin-header {
            padding: var(--ks-spacing-3);
        }

        .plugins-container {
            padding-left: var(--ks-spacing-3);
            padding-right: var(--ks-spacing-3);
        }
    }
</style>
