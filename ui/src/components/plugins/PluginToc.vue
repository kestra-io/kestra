<template>
    <KsSideBar class="plugin-toc" aria-label="Plugins">
        <template #header>
            <KsSearch
                class="plugin-toc__search"
                :placeholder="$t('pluginPage.searchTasks', {count: totalElementsCount})"
                v-model="searchInput"
                clearable
            />
        </template>

        <template v-if="subGroupWrappers.length > 1">
            <KsSideBarSection
                v-for="sub in visibleSubGroups"
                :key="sub.subGroup"
                :title="sectionTitle(sub)"
                collapsible
                :defaultCollapsed="!isSubGroupOpen(sub)"
            >
                <PluginTocItems
                    :groupedElements="filteredGroupedElements(sub)"
                    :icons="allIcons"
                    @navigate="handleNavigate"
                />
            </KsSideBarSection>
        </template>

        <template v-else-if="directWrapper && directHasMatches">
            <PluginTocItems
                :groupedElements="filteredGroupedElements(directWrapper)"
                :icons="allIcons"
                @navigate="handleNavigate"
            />
        </template>
    </KsSideBar>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useRoute} from "vue-router"
    import {KsSearch} from "@kestra-io/design-system"
    import {isEntryAPluginElementPredicate, type Plugin, type PluginElement} from "../../utils/pluginUtils"
    import {usePluginsStore} from "../../stores/plugins"
    import PluginTocItems from "./PluginTocItems.vue"

    const props = defineProps<{
        plugins: Plugin[];
    }>()

    const emit = defineEmits<{
        routerChange: [];
    }>()

    const route = useRoute()
    const pluginsStore = usePluginsStore()

    const searchInput = ref<string>("")
    const searchLower = computed(() => searchInput.value.toLowerCase())

    const allIcons = computed(() => ({
        ...pluginsStore.icons,
        ...pluginsStore.groupIcons,
    }))

    const subGroupWrappers = computed(() => props.plugins.filter(p => p.subGroup))
    const directWrapper = computed<Plugin | undefined>(() => {
        if (subGroupWrappers.value.length === 1) {
            return subGroupWrappers.value[0]
        }
        return props.plugins.find(p => !p.subGroup)
    })

    const hasMatches = (plugin: Plugin): boolean =>
        Object.values(filteredGroupedElements(plugin)).some(arr => arr.length > 0)

    const visibleSubGroups = computed(() => {
        if (!searchLower.value) return subGroupWrappers.value
        return subGroupWrappers.value.filter(hasMatches)
    })

    const directHasMatches = computed(() => directWrapper.value && hasMatches(directWrapper.value))

    const matchesSearch = (cls: string) => {
        if (!searchLower.value) return true
        return cls.toLowerCase().includes(searchLower.value)
    }

    const groupedElements = (plugin: Plugin): Record<string, PluginElement[]> => {
        return Object.fromEntries(
            Object.entries(plugin)
                .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                .map(([key, value]) => [
                    key,
                    (value as PluginElement[]).filter(el => !el.deprecated),
                ]),
        )
    }

    const filteredGroupedElements = (plugin: Plugin): Record<string, PluginElement[]> => {
        const raw = groupedElements(plugin)
        if (!searchLower.value) return raw
        const filtered: Record<string, PluginElement[]> = {}
        for (const [type, els] of Object.entries(raw)) {
            const kept = els.filter(el => matchesSearch(el.cls))
            if (kept.length) filtered[type] = kept
        }
        return filtered
    }

    const elementsCount = (plugin: Plugin): number =>
        Object.values(filteredGroupedElements(plugin)).reduce((acc, els) => acc + els.length, 0)

    const totalElementsCount = computed<number>(() =>
        props.plugins.reduce(
            (acc, p) => acc + Object.values(groupedElements(p)).reduce((s, els) => s + els.length, 0),
            0,
        ),
    )

    const sectionTitle = (sub: Plugin): string => {
        const base = sub.title?.capitalize() ?? ""
        const count = elementsCount(sub)
        return count > 0 ? `${base} (${count})` : base
    }

    const isSubGroupOpen = (sub: Plugin): boolean => {
        const cls = route.params.cls as string | undefined
        if (!cls) return false
        if (sub.subGroup && cls.startsWith(sub.subGroup + ".")) return true
        return searchLower.value !== ""
            && Object.values(filteredGroupedElements(sub)).some(arr => arr.length > 0)
    }

    const handleNavigate = () => {
        emit("routerChange")
    }
</script>

<style scoped lang="scss">
    .plugin-toc {
        height: 100%;

        &__search {
            width: 100%;
        }
    }
</style>
