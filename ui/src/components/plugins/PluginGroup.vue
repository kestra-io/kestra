<template>
    <PluginLayout
        :title
        :breadcrumb
        :sidebarPlugins
        :headerTitle
        :headerIconCls
        :icons="allIcons"
        :isEnterpriseEdition
        :shortDescription="pluginShortDescription"
        :longDescription
        :backTo
        flat
        @router-change="onRouterChange"
    >
        <template v-if="groupPlugin">
            <section v-if="isShowingSubGroups" class="plugin-group">
                <h5 class="plugin-group__title">
                    {{ $t("pluginPage.group.plugins", {count: childSubGroups.length}) }}
                </h5>
                <div class="plugin-group__grid">
                    <KsPluginCard
                        v-for="sub in childSubGroups"
                        :key="sub.subGroup"
                        :iconCls="sub.subGroup"
                        :icons="allIcons"
                        :title="sub.title"
                        :description="sub.description"
                        :categories="sub.categories"
                        :taskCount="elementCountFor(sub)"
                        :blueprintCount="blueprintCountFor(sub)"
                        @click="openSubGroup(sub)"
                    />
                </div>
            </section>

            <section v-else-if="subElements.length" class="plugin-group">
                <h5 class="plugin-group__title">
                    {{ $t("pluginPage.group.tasks", {count: subElements.length}) }}
                </h5>
                <div class="plugin-group__grid">
                    <KsPluginCard
                        v-for="el in subElements"
                        :key="el.cls"
                        :iconCls="el.cls"
                        :icons="allIcons"
                        :title="shortClassName(el.cls)"
                        :description="el.title"
                        @click="openTask(el.cls)"
                    />
                </div>
            </section>

            <section v-if="groupBlueprints.length > 0" class="plugin-group plugin-group--blueprints">
                <h5 class="plugin-group__title">
                    {{ $t("pluginPage.group.blueprints", {count: groupBlueprints.length}) }}
                </h5>
                <div class="plugin-group__grid">
                    <KsPluginCard
                        v-for="bp in groupBlueprints"
                        :key="bp.id"
                        :title="bp.title"
                        :description="bp.description"
                        @click="openBlueprint(bp.id)"
                    >
                        <template #footer-content>
                            <BlueprintIconStack :clses="bp.includedTasks ?? []" :icons="allIcons" />
                        </template>
                    </KsPluginCard>
                </div>
            </section>
        </template>

        <KsEmpty
            v-else-if="pluginsStore.plugins"
            :description="$t('pluginPage.notFound')"
            class="plugin-group__state"
        />

        <div v-else class="plugin-group__state">
            <KsSkeleton animated :rows="6" />
        </div>
    </PluginLayout>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import axios from "axios"
    import {KsPluginCard, KsEmpty, KsSkeleton, type KsBreadcrumbItem} from "@kestra-io/design-system"
    import PluginLayout from "./PluginLayout.vue"
    import BlueprintIconStack from "./BlueprintIconStack.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import {usePluginsEnrichmentStore} from "../../stores/pluginsEnrichment"
    import {useMiscStore} from "override/stores/misc"
    import {isEntryAPluginElementPredicate, isEnterpriseEditionPlugin, type Plugin, type PluginElement} from "../../utils/pluginUtils"
    import useRouteContext from "../../composables/useRouteContext"
    import {API_URL} from "../../stores/api"

    type Blueprint = {
        id: string;
        title: string;
        description?: string;
        includedTasks?: string[];
    }

    const pluginsStore = usePluginsStore()
    const enrichmentStore = usePluginsEnrichmentStore()
    const miscStore = useMiscStore()
    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    const groupBlueprints = ref<Blueprint[]>([])

    const allIcons = computed(() => ({
        ...pluginsStore.icons,
        ...pluginsStore.groupIcons,
    }))

    const groupPlugin = computed<Plugin | undefined>(() => {
        const name = route.params.name as string | undefined
        const subGroup = route.params.subGroup as string | undefined
        return pluginsStore.findPluginByName(name, subGroup) ?? undefined
    })

    const parentGroup = computed<Plugin | undefined>(() => {
        const g = groupPlugin.value
        if (!g?.subGroup) return undefined
        return pluginsStore.findPluginByName(g.name) ?? undefined
    })

    const childSubGroups = computed<Plugin[]>(() => {
        const g = groupPlugin.value
        if (!g || g.subGroup) return []
        return (pluginsStore.plugins ?? []).filter(p => p.name === g.name && p.subGroup)
    })

    const isShowingSubGroups = computed<boolean>(() => childSubGroups.value.length > 0)

    const subElements = computed<PluginElement[]>(() => {
        const plugin = groupPlugin.value
        if (!plugin) return []
        const out: PluginElement[] = []
        for (const [key, value] of Object.entries(plugin)) {
            if (isEntryAPluginElementPredicate(key, value)) {
                for (const el of value) {
                    if (!el?.deprecated) {
                        out.push(el)
                    }
                }
            }
        }
        return out.sort((a, b) => shortClassName(a.cls).localeCompare(shortClassName(b.cls)))
    })

    const headerTitle = computed<string>(() => groupPlugin.value?.title ?? "")

    const headerIconCls = computed<string | undefined>(() => {
        const g = groupPlugin.value
        if (g?.subGroup) return g.subGroup
        if (g?.group && allIcons.value?.[g.group]) return g.group
        return childSubGroups.value[0]?.subGroup ?? g?.group
    })

    const pluginShortDescription = computed<string | null>(() =>
        enrichmentStore.getEnrichment(groupPlugin.value)?.description
        ?? groupPlugin.value?.description
        ?? null,
    )

    const longDescription = computed<string | null>(() =>
        enrichmentStore.getEnrichment(groupPlugin.value)?.body
        ?? groupPlugin.value?.longDescription
        ?? null,
    )

    const isEnterpriseEdition = computed<boolean>(() => isEnterpriseEditionPlugin(groupPlugin.value?.group))

    const sidebarPlugins = computed<Plugin[]>(() => pluginsStore.sidebarPluginsFor({owner: groupPlugin.value}))

    const backTo = computed(() => {
        const parent = parentGroup.value
        if (parent) {
            return {name: "plugins/group", params: {name: parent.name}}
        }
        return {name: "plugins/list"}
    })

    const breadcrumb = computed<KsBreadcrumbItem[]>(() => {
        const crumbs: KsBreadcrumbItem[] = [
            {label: t("plugins.names"), link: {name: "plugins/list"}},
        ]
        if (parentGroup.value?.title) {
            crumbs.push({
                label: parentGroup.value.title,
                link: {name: "plugins/group", params: {name: parentGroup.value.name}},
            })
        }
        return crumbs
    })

    const title = computed(() => groupPlugin.value?.title ?? t("plugins.names"))

    const routeInfo = computed(() => ({title: title.value, breadcrumb: breadcrumb.value}))
    useRouteContext(routeInfo)

    function shortClassName(cls: string): string {
        const lastDot = cls.lastIndexOf(".")
        return lastDot === -1 ? cls : cls.substring(lastDot + 1)
    }

    function elementCountFor(plugin: Plugin): number {
        let count = 0
        for (const [key, value] of Object.entries(plugin)) {
            if (isEntryAPluginElementPredicate(key, value)) {
                count += value.filter(el => !el?.deprecated).length
            }
        }
        return count
    }

    function blueprintCountFor(plugin: Plugin): number {
        return enrichmentStore.getEnrichment(plugin)?.blueprintCount ?? 0
    }

    function openTask(cls: string) {
        router.push({name: "plugins/view", params: {tenant: route.params.tenant as string, cls}})
    }

    function openSubGroup(sub: Plugin) {
        if (!sub?.name) return
        const params: Record<string, string> = {name: sub.name}
        if (sub.subGroup) params.subGroup = sub.subGroup
        router.push({name: "plugins/group", params})
    }

    function openBlueprint(id: string) {
        router.push({
            name: "blueprints/view",
            params: {kind: "flow", tab: "community", blueprintId: id},
        })
    }

    function onRouterChange() {
        window.scroll({top: 0, behavior: "smooth"})
    }

    async function loadGroupBlueprints() {
        groupBlueprints.value = []
        const cls = groupPlugin.value?.subGroup ?? groupPlugin.value?.group
        if (!cls) return

        try {
            const response = await axios.get<Blueprint[]>(
                `${API_URL}/v1/blueprints/plugin/${cls}/version/latest`,
            )
            groupBlueprints.value = response.data ?? []
        } catch (err) {
            console.warn("Failed to load blueprints for group", cls, err)
            groupBlueprints.value = []
        }
    }

    async function loadGroupIcons() {
        try {
            await pluginsStore.ensureGroupIcons()
        } catch (err) {
            console.warn("Failed to load group icons", err)
        }
    }

    watch(
        () => groupPlugin.value,
        (g) => {
            if (g) loadGroupBlueprints()
        },
        {immediate: true},
    )

    onMounted(() => {
        miscStore.loadConfigs()
        pluginsStore.ensurePlugins()
        loadGroupIcons()
    })
</script>

<style scoped lang="scss">
    .plugin-group {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);

        &__title {
            margin: 0;
            font-size: var(--ks-font-size-sm);
            font-weight: 600;
            color: var(--ks-text-primary);
        }

        &__grid {
            display: grid;
            gap: var(--ks-spacing-3);
            grid-template-columns: repeat(auto-fill, minmax(17.5rem, 1fr));
        }

        &--blueprints {
            margin-top: var(--ks-spacing-6);
        }

        &__state {
            padding: var(--ks-spacing-6) 0;
        }
    }
</style>
