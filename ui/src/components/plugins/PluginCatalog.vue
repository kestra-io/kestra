<template>
    <TopNavBar :title="title">
        <template v-if="catalogExtension.pageActions" #actions>
            <component :is="catalogExtension.pageActions" />
        </template>
    </TopNavBar>

    <div v-if="loadError" class="plugins-load-error">
        <KsAlert type="error" :closable="false" :title="$t('pluginPage.loadErrorTitle')" data-test="plugins-load-error">
            {{ $t("pluginPage.loadErrorDescription") }}
            <div class="plugins-load-error-action">
                <KsButton size="small" :icon="Refresh" @click="loadPlugins">
                    {{ $t("retry") }}
                </KsButton>
            </div>
        </KsAlert>
    </div>

    <section v-else-if="!filteredPlugins" class="plugins-container plugins-container--loading">
        <KsSkeleton v-for="n in 8" :key="n" animated :rows="4" class="plugin-skeleton" />
    </section>

    <template v-else>
        <component :is="catalogExtension.banner" v-if="catalogExtension.banner" :catalog="baseList" />

        <div class="filter-toolbar">
            <div class="filter-toolbar__search">
                <KsSearch
                    v-model="searchText"
                    :placeholder="totalPlugins ? $t('pluginPage.search', {count: totalPlugins}) : $t('search')"
                    clearable
                />
            </div>

            <div class="category-tags">
                <KsCheckTag
                    v-for="category in availableCategories"
                    :key="category"
                    class="category-tag"
                    pill
                    :checked="selectedCategories.includes(category)"
                    @change="toggleCategory(category)"
                >
                    <template #icon>
                        <component :is="CATEGORY_ICONS[category] ?? TagOutline" :size="14" />
                    </template>
                    <span class="category-label">{{ category }}</span>
                </KsCheckTag>
            </div>

            <div class="filter-toolbar-end">
                <component :is="catalogExtension.toolbar" v-if="catalogExtension.toolbar" :catalog="baseList" />

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
        </div>

        <component
            :is="catalogExtension.table"
            v-if="catalogExtension.table && view === 'table'"
            :plugins="pluginsList"
            :catalog="baseList"
            :search="searchInput"
            :categories="selectedCategories"
        />

        <div v-else-if="pluginsList.length === 0 && extraCardCount === 0" class="plugins-empty" data-test="plugins-empty">
            <KsNoData :title="emptyCopy.title">
                {{ emptyCopy.description }}
            </KsNoData>
            <KsButton data-test="plugins-show-all" @click="showAllPlugins">
                {{ $t("pluginPage.showAll") }}
            </KsButton>
        </div>

        <section v-else class="plugins-container">
            <KsTooltip
                v-for="plugin in pluginsList"
                :showAfter="1000"
                :key="plugin.name"
            >
                <template #content>
                    <div class="tasks-tooltips">
                        <template
                            v-for="([elementType, elements]) in allElementsByTypeEntries(plugin)"
                            :key="elementType"
                        >
                            <p
                                v-if="elements.length > 0"
                                class="task-type"
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
                <PluginCard
                    :iconCls="plugin.group"
                    :icons
                    :loadIcon="pluginsStore.loadIcon"
                    :title="enrichmentStore.getEnrichment(plugin)?.title ?? plugin.title.capitalize()"
                    :description="plugin.description"
                    :categories="plugin.categories"
                    :taskCount="taskCount(plugin)"
                    :blueprintCount="blueprintCount(plugin)"
                    @click="openGroup(plugin)"
                >
                    <template v-if="catalogExtension.cardStatus" #status>
                        <component :is="catalogExtension.cardStatus" :plugin />
                    </template>
                </PluginCard>
            </KsTooltip>
            <component
                :is="catalogExtension.extraCards"
                v-if="catalogExtension.extraCards"
                :catalog="baseList"
                :search="searchInput"
                :categories="selectedCategories"
            />
        </section>
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, markRaw, onMounted, watch, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {KsSearch, KsAlert, KsButton, KsSkeleton} from "@kestra-io/design-system"
    import PluginCard from "./PluginCard.vue"
    import {usePluginView, useQueryChoice} from "./pluginView"
    import {isEntryAPluginElementPredicate, isPluginMatched, latestVersionEntries, pluginElementClasses, type Plugin, type PluginElement} from "../../utils/pluginUtils"
    import {usePluginsStore} from "../../stores/plugins"
    import {usePluginsEnrichmentStore} from "../../stores/pluginsEnrichment"
    import {usePluginsCount} from "../../composables/usePluginsCount"
    import {useMiscStore} from "override/stores/misc"
    import {usePluginCatalogExtension} from "override/components/plugins/pluginCatalogExtension"
    import useRouteContext from "../../composables/useRouteContext"
    import TopNavBar from "../../components/layout/TopNavBar.vue"

    import Refresh from "vue-material-design-icons/Refresh.vue"
    import BriefcaseOutline from "vue-material-design-icons/BriefcaseOutline.vue"
    import CloudOutline from "vue-material-design-icons/CloudOutline.vue"
    import CogOutline from "vue-material-design-icons/CogOutline.vue"
    import Creation from "vue-material-design-icons/Creation.vue"
    import PackageVariantClosed from "vue-material-design-icons/PackageVariantClosed.vue"
    import SourceBranch from "vue-material-design-icons/SourceBranch.vue"
    import TagOutline from "vue-material-design-icons/TagOutline.vue"
    import WrenchOutline from "vue-material-design-icons/WrenchOutline.vue"

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()
    const pluginsStore = usePluginsStore()
    const miscStore = useMiscStore()
    const enrichmentStore = usePluginsEnrichmentStore()
    const {totalPlugins} = usePluginsCount()
    const catalogExtension = usePluginCatalogExtension()
    const view = usePluginView()

    const title = computed(() => t("plugins.names"))
    const routeInfo = computed(() => ({title: title.value, breadcrumb: undefined}))
    useRouteContext(routeInfo)

    const filteredPlugins = ref<Plugin[] | undefined>(undefined)
    const loadError = ref(false)

    const icons = computed(() => ({
        ...pluginsStore.icons,
        ...pluginsStore.groupIcons,
    }))
    const searchText = computed<string>({
        get: () => typeof route.query.q === "string" ? route.query.q : "",
        set: (q) => router.replace({query: {...route.query, q: q || undefined}}),
    })
    const selectedCategories = computed<string[]>(() =>
        [route.query.categories ?? []].flat().filter((category): category is string => typeof category === "string"),
    )

    const SORT_KEYS = ["nameAsc", "nameDesc", "newest", "mostUsed"] as const
    type SortKey = typeof SORT_KEYS[number]
    const sortBy = useQueryChoice("sort", SORT_KEYS)

    const sortOptions = computed<{value: SortKey, label: string}[]>(() => [
        {value: "nameAsc", label: t("pluginPage.sort.nameAsc")},
        {value: "nameDesc", label: t("pluginPage.sort.nameDesc")},
        {value: "newest", label: t("pluginPage.sort.newest")},
        {value: "mostUsed", label: t("pluginPage.sort.mostUsed")},
    ])

    const searchInput = computed(() => searchText.value.toLowerCase())

    const pluginTitle = (plugin: Plugin): string => String(plugin?.manifest?.["X-Kestra-Title"] ?? plugin?.title ?? "").toLowerCase()

    const baseList = computed<Plugin[]>(() => {
        return latestVersionEntries(filteredPlugins.value ?? [])
            .filter(plugin => !plugin.subGroup && isVisible(plugin))
    })

    const CATEGORY_ICONS: Record<string, Component> = markRaw({
        AI: Creation,
        BUSINESS: BriefcaseOutline,
        CLOUD: CloudOutline,
        CORE: PackageVariantClosed,
        DATA: SourceBranch,
        INFRASTRUCTURE: CogOutline,
        TOOL: WrenchOutline,
    })

    const availableCategories = computed<string[]>(() =>
        [...new Set(baseList.value.flatMap(p => p.categories ?? []))].sort(),
    )

    const subGroupTitlesByName = computed(() => {
        const titles = new Map<string, string[]>()
        for (const plugin of filteredPlugins.value ?? []) {
            if (plugin.subGroup && plugin.title) {
                titles.set(plugin.name, [...(titles.get(plugin.name) ?? []), plugin.title.toLowerCase()])
            }
        }
        return titles
    })

    const matchesSearch = (plugin: Plugin): boolean =>
        isPluginMatched(plugin, searchInput.value)
        || (subGroupTitlesByName.value.get(plugin.name) ?? []).some(title => title.includes(searchInput.value))

    const toggleCategory = (category: string) => {
        const categories = selectedCategories.value.includes(category)
            ? selectedCategories.value.filter(selected => selected !== category)
            : [...selectedCategories.value, category]
        router.replace({query: {...route.query, categories: categories.length > 0 ? categories : undefined}})
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
            .filter(plugin => matchesSearch(plugin))
            .filter(plugin => matchesSelectedCategories(plugin))
            .filter(plugin => catalogExtension.matches?.(plugin) ?? true)
            .sort(comparators[sortBy.value])
    })

    const extraCardCount = computed(() => catalogExtension.extraCardCount?.(baseList.value, searchInput.value, selectedCategories.value) ?? 0)

    const isFiltered = computed(() => searchInput.value !== "" || selectedCategories.value.length > 0)

    const emptyCopy = computed(() => catalogExtension.emptyCopy?.(isFiltered.value) ?? {
        title: t("pluginPage.empty.title"),
        description: t("pluginPage.empty.description"),
    })

    const showAllPlugins = async () => {
        // Awaited so that reset() writes its own query keys on top of this one instead of over a stale route.query.
        await router.replace({query: {...route.query, q: undefined, categories: undefined}})
        catalogExtension.reset?.()
    }

    const loadPlugins = () => {
        loadError.value = false
        pluginsStore.ensurePlugins().catch((err) => {
            console.error("Failed to load plugins", err)
            loadError.value = true
        })
    }

    const loadPluginIcons = async () => {
        try {
            await pluginsStore.ensureGroupIcons()
        } catch (error) {
            console.error("Failed to load plugin icons:", error)
        }
    }

    const openGroup = (plugin: Plugin) => {
        if (!plugin?.name) return
        router.push({name: "plugins/group", params: {...route.params as Record<string, string>, name: plugin.name}})
    }

    const isVisible = (plugin: Plugin) => taskCount(plugin) > 0

    const allElementsByTypeEntries = (plugin: Plugin): [string, string[]][] => {
        return Object.entries(plugin)
            .filter((entry): entry is [string, PluginElement[]] => isEntryAPluginElementPredicate(entry[0], entry[1]))
            .map(([elementType, elements]) => [
                elementType,
                elements.filter(({deprecated}) => !deprecated).map(({cls}) => cls),
            ])
    }

    const taskCount = (plugin: Plugin): number => pluginElementClasses(plugin).length

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
        pluginsStore.fetchIcons()
        miscStore.loadConfigs()
        loadPlugins()
        enrichmentStore.fetchEnrichment()
        catalogExtension.load?.()
    })
</script>

<style scoped>
    .plugins-load-error {
        padding: var(--ks-spacing-6);
    }

    .plugins-load-error-action {
        margin-top: var(--ks-spacing-2);
    }

    .plugins-empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-6);
    }

    .filter-toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-3);
        width: 100%;
        padding: var(--ks-spacing-6);
    }

    .filter-toolbar__search {
        flex: 0 1 17rem;
        min-width: 12rem;
    }

    .filter-toolbar-end {
        flex: 1 1 100%;
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: flex-end;
        gap: var(--ks-spacing-3);
    }

    .filter-toolbar {
        .category-tags {
            display: flex;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2);
            min-width: 0;
        }

        .category-tag {
            gap: var(--ks-spacing-1);
            padding: var(--ks-spacing-1) var(--ks-spacing-3);
            font-weight: normal;
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
    }

    .plugins-container--loading {
        margin-top: var(--ks-spacing-4);
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

    .task-type {
        margin-bottom: 0;
    }

    @media (max-width: 650px) {
        .plugins-container {
            padding-left: var(--ks-spacing-3);
            padding-right: var(--ks-spacing-3);
        }
    }
</style>
