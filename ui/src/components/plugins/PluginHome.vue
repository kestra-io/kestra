<template>
    <DottedLayout
        :embed="embed"
        :phrase="$t('pluginPage.title2')"
        :alt="$t('pluginPage.alt')"
        :image="headerImage"
        :imageDark="headerImageDark"
    >
        <KsRow class="my-4 px-3" justify="center">
            <KSFilter
                :configuration="pluginFilter"
                :buttons="{
                    savedFilters: {shown: false},
                    tableOptions: {shown: false}
                }"
                :searchInputFullWidth="true"
                @search="handleSearch"
            />
        </KsRow>
        <section class="px-3 plugins-container">
            <KsTooltip
                v-for="(plugin, index) in pluginsList"
                :showAfter="1000"
                :key="`${plugin.name}-${index}`"
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
                                    <span @click="openPlugin(element)">{{ element }}</span>
                                </li>
                            </ul>
                        </template>
                    </div>
                </template>
                <div class="plugin-card" @click="openGroup(plugin)">
                    <KsTaskIcon
                        class="size"
                        :onlyIcon="true"
                        :cls="hasIcon(plugin.subGroup) ? plugin.subGroup : plugin.group"
                        :icons="icons"
                    />
                    <span class="text-truncate">{{ plugin.title.capitalize() }}</span>
                </div>
            </KsTooltip>
        </section>
    </DottedLayout>
</template>

<script setup lang="ts">
    import {ref, computed, onBeforeMount, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import {isEntryAPluginElementPredicate, isPluginMatched} from "../../utils/pluginUtils"
    import DottedLayout from "../layout/DottedLayout.vue"
    import {KsFilter as KSFilter} from "@kestra-io/design-system"
    import {usePluginFilter} from "../filter/configurations"
    import headerImage from "../../assets/icons/plugin.svg"
    import headerImageDark from "../../assets/icons/plugin-dark.svg"
    import {usePluginsStore} from "../../stores/plugins"
    import useRestoreUrl from "../../composables/useRestoreUrl"

    const route = useRoute()
    const router = useRouter()
    const pluginsStore = usePluginsStore()

    const pluginFilter = usePluginFilter()

    const props = withDefaults(defineProps<{
        plugins: any[],
        embed?: boolean
    }>(), {
        embed: false,
    })

    const {saveRestoreUrl} = useRestoreUrl()

    const icons = ref<Record<string, any>>({})
    const searchText = ref("")

    const handleSearch = (query: string) => {
        searchText.value = query
    }

    const searchInput = computed(() => searchText.value.toLowerCase())

    const pluginsList = computed(() => {
        // Show subgroups only if exist, else show main group - GH-8940
        const grouped = props.plugins.reduce((acc: Record<string, any[]>, plugin) => {
            (acc[plugin.group] ??= []).push(plugin)
            return acc
        }, {})

        const filtered = Object.values(grouped).flatMap(group =>
            group.filter((p: any) => p.subGroup).length ? group.filter((p: any) => p.subGroup) : group.filter((p: any) => !p.subGroup),
        )

        return filtered
            .filter((plugin, index, self) =>
                index === self.findIndex(t => t.title === plugin.title && t.group === plugin.group),
            )
            .filter(plugin => isPluginMatched(plugin, searchInput.value))
            .filter(plugin => isVisible(plugin))
            .sort((a, b) => {
                const nameA = a.manifest["X-Kestra-Title"].toLowerCase()
                const nameB = b.manifest["X-Kestra-Title"].toLowerCase()
                return nameA < nameB ? -1 : nameA > nameB ? 1 : 0
            })
    })

    const loadPluginIcons = async () => {
        try {
            icons.value = await pluginsStore.groupIcons()
        } catch (error) {
            console.error("Failed to load plugin icons:", error)
            icons.value = {}
        }
    }

    const openGroup = (plugin: any) => {
        const defaultElement = Object.entries(plugin)
            .filter(([elementType, elements]) => isEntryAPluginElementPredicate(elementType, elements))
            .flatMap((entry) => (entry[1] as any[]).filter(({deprecated}: any) => !deprecated).map(({cls}: any) => cls))?.[0]
        openPlugin(defaultElement)
    }

    const openPlugin = (cls: string) => {
        if (!cls) {
            return
        }
        router.push({
            name: "plugins/view",
            params: {
                ...route.params,
                cls: cls,
            },
        })
    }

    const isVisible = (plugin: any) => {
        return allElements(plugin).length > 0
    }

    const hasIcon = (cls: string) => {
        return icons.value[cls] !== undefined
    }

    const allElementsByTypeEntries = (plugin: any): [string, string[]][] => {
        return Object.entries(plugin).filter(([elementType, elements]) => isEntryAPluginElementPredicate(elementType, elements))
            .map(([elementType, elements]) => [
                elementType,
                (elements as any[]).filter(({deprecated}: any) => !deprecated).map(({cls}: any) => cls),
            ])
    }

    const allElements = (plugin: any) => {
        return allElementsByTypeEntries(plugin).flatMap((entry) => entry[1] as any[])
    }

    onBeforeMount(() => {
        loadPluginIcons()
        searchText.value = String(route.query?.["filters[q][EQUALS]"] ?? "")
    })

    watch(() => route.query["filters[q][EQUALS]"], (newQ) => {
        searchText.value = String(newQ ?? "")
        saveRestoreUrl()
    })
</script>

<style scoped lang="scss">
    .plugins-container {
        display: grid;
        gap: 16px;
        grid-template-columns: repeat(auto-fill, minmax(232px, 1fr));
        padding-bottom: 4rem;
    }

    .tasks-tooltips {
        max-height: 20rem;
        overflow-y: auto;
        overflow-x: hidden;

        span {
            cursor: pointer;
        }

        &.enhance-readability {
            padding: 1.5rem;
            background-color: var(--ks-bg-tag);
        }
    }

    .plugin-card {
        display: flex;
        width: 100%;
        min-width: 130px;
        padding: 8px 16px;
        align-items: center;
        gap: 8px;
        border-radius: 4px;
        text-overflow: ellipsis;
        font-size: var(--ks-font-size-xs);
        font-weight: 700;
        line-height: 26px;
        cursor: pointer;

        border: 1px solid var(--ks-border-default);
        background-color: var(--ks-btn-secondary-bg-default);
        color: var(--ks-text-primary);

        &:hover {
            border-color: var(--ks-border-focus);
            background-color: var(--ks-btn-secondary-bg-hover);
        }
    }

    .size {
        height: 2em;
        width: 2em;
    }
</style>