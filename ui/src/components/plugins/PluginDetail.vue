<template>
    <PluginLayout
        :title
        :breadcrumb
        :sidebarPlugins
        :headerTitle
        :headerIconCls
        :icons="allIcons"
        :elementTypeLabel
        :isEnterpriseEdition
        :shortDescription="pluginShortDescription"
        :backTo
        @router-change="onRouterChange"
    >
        <KsAlert v-if="loadError" type="error" :closable="false">
            {{ $t("pluginPage.notFound") }}
        </KsAlert>

        <div v-else-if="loading" class="plugin-schema-state">
            <KsSkeleton animated :rows="10" />
        </div>

        <Suspense v-else-if="pluginsStore.plugin && pluginType">
            <SchemaToHtml
                class="plugin-schema"
                :darkMode="isDarkTheme"
                :schema="pluginsStore.plugin.schema"
                :propsInitiallyExpanded="true"
                :pluginType="pluginType"
                noUrlChange
            >
                <template #markdown="{content}">
                    <KsMarkdown :content="content" />
                </template>
            </SchemaToHtml>
            <template #fallback>
                <div class="plugin-schema-state">
                    <KsSkeleton animated :rows="10" />
                </div>
            </template>
        </Suspense>
    </PluginLayout>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import SchemaToHtml from "./schema/SchemaToHtml.vue"
    import {KsMarkdown, KsAlert, KsSkeleton, type KsBreadcrumbItem} from "@kestra-io/design-system"
    import PluginLayout from "./PluginLayout.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import {useMiscStore} from "override/stores/misc"
    import {isEntryAPluginElementPredicate, isEnterpriseEditionPlugin, type Plugin} from "../../utils/pluginUtils"
    import {getTheme} from "../../utils/utils"
    import useRouteContext from "../../composables/useRouteContext"

    const pluginsStore = usePluginsStore()
    const miscStore = useMiscStore()
    const isDarkTheme = computed(() => {
        void miscStore.theme
        return getTheme() === "dark"
    })
    const route = useRoute()
    const {t, te} = useI18n()

    const pluginType = ref<string | undefined>(undefined)
    const loading = ref(true)
    const loadError = ref(false)

    const allIcons = computed(() => ({
        ...pluginsStore.icons,
        ...pluginsStore.groupIcons,
    }))

    const routeCls = computed<string | undefined>(() => route.params.cls as string | undefined)

    const owningPlugin = computed<Plugin | null>(() => pluginsStore.findPluginByCls(routeCls.value))

    const parentGroup = computed<Plugin | undefined>(() => {
        const grp = owningPlugin.value
        if (!grp?.subGroup) return undefined
        return pluginsStore.findPluginByName(grp.name) ?? undefined
    })

    const pluginName = computed<string | undefined>(() => {
        const split = routeCls.value?.split(".")
        return split ? split[split.length - 1] : undefined
    })

    const headerTitle = computed<string>(() => pluginName.value ?? "")
    const headerIconCls = computed<string | undefined>(() => routeCls.value)

    const isEnterpriseEdition = computed<boolean>(() => isEnterpriseEditionPlugin(routeCls.value))

    const elementTypeLabel = computed<string | null>(() => {
        const cls = routeCls.value
        const plugin = owningPlugin.value
        if (!cls || !plugin) return null
        for (const [key, value] of Object.entries(plugin)) {
            if (isEntryAPluginElementPredicate(key, value) && value.some(el => el?.cls === cls)) {
                const i18nKey = `pluginPage.elementType.${key}`
                return te(i18nKey) ? t(i18nKey) : null
            }
        }
        return null
    })

    const pluginShortDescription = computed<string | null>(() => {
        const schema = pluginsStore.plugin?.schema
        return schema?.properties?.title ?? schema?.title ?? null
    })

    const sidebarPlugins = computed<Plugin[]>(() => pluginsStore.sidebarPluginsFor({cls: routeCls.value}))

    const backTo = computed(() => {
        const grp = owningPlugin.value
        if (grp) {
            const params: Record<string, string> = {name: grp.name}
            if (grp.subGroup) params.subGroup = grp.subGroup
            return {name: "plugins/group", params}
        }
        return {name: "plugins/list"}
    })

    const breadcrumb = computed<KsBreadcrumbItem[]>(() => {
        const crumbs: KsBreadcrumbItem[] = [
            {label: t("plugins.names"), link: {name: "plugins/list"}},
        ]
        const grp = owningPlugin.value
        if (grp) {
            if (grp.subGroup && parentGroup.value?.title) {
                crumbs.push({
                    label: parentGroup.value.title,
                    link: {name: "plugins/group", params: {name: parentGroup.value.name}},
                })
            }
            if (grp.title) {
                crumbs.push({
                    label: grp.title,
                    link: {
                        name: "plugins/group",
                        params: {name: grp.name, subGroup: grp.subGroup},
                    },
                })
            }
        }
        return crumbs
    })

    const title = computed(() => pluginName.value ?? routeCls.value ?? t("plugins.names"))
    const routeInfo = computed(() => ({title: title.value, breadcrumb: breadcrumb.value}))
    useRouteContext(routeInfo)

    const hash = computed(() => miscStore.configs?.pluginsHash ?? 0)

    async function loadPlugin() {
        const clsParam = route.params.cls as string | undefined
        if (!clsParam) return

        loading.value = true
        loadError.value = false
        const versionParam = route.params.version as string | undefined
        try {
            await pluginsStore.load({version: versionParam, hash: hash.value, cls: clsParam})
        } catch (err) {
            console.error("Failed to load plugin", clsParam, err)
            loadError.value = true
        } finally {
            pluginType.value = clsParam
            loading.value = false
        }
    }

    function onRouterChange() {
        window.scroll({top: 0, behavior: "smooth"})
    }

    async function loadGroupIcons() {
        try {
            await pluginsStore.ensureGroupIcons()
        } catch (err) {
            console.warn("Failed to load group icons", err)
        }
    }

    watch(
        () => [route.params.cls, route.params.version],
        () => loadPlugin(),
    )

    onMounted(() => {
        miscStore.loadConfigs()
        pluginsStore.ensurePlugins()
        loadPlugin()
        loadGroupIcons()
    })
</script>
