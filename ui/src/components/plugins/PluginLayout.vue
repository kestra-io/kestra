<template>
    <TopNavBar :title="title" :breadcrumb="breadcrumb" />
    <div class="plugin-page">
        <PluginToc
            v-if="sidebarPlugins.length"
            class="plugin-page__sidebar"
            :plugins="sidebarPlugins"
            @router-change="$emit('router-change')"
        />
        <div class="plugin-page__body">
            <div class="plugin-detail">
                <div :class="['plugin-detail__main', {'plugin-detail__main--flat': flat}]">
                    <header class="plugin-header">
                        <div class="plugin-header__row">
                            <KsIconButton
                                class="plugin-header__back"
                                :ariaLabel="$t('pluginPage.header.back')"
                                :to="backTo"
                            >
                                <ArrowLeft />
                            </KsIconButton>

                            <KsTaskIcon
                                class="plugin-header__logo"
                                :cls="headerIconCls"
                                onlyIcon
                                :icons="icons"
                            />

                            <div class="plugin-header__body">
                                <div class="plugin-header__title-row">
                                    <h4 class="plugin-header__name">{{ headerTitle }}</h4>
                                    <div class="plugin-header__tags">
                                        <KsTag v-if="elementTypeLabel" size="small">
                                            {{ elementTypeLabel }}
                                        </KsTag>
                                        <KsTag size="small" type="success">
                                            <template #icon>
                                                <CheckCircle />
                                            </template>
                                            {{ $t("pluginPage.header.certified") }}
                                        </KsTag>
                                        <KsTag v-if="isEnterpriseEdition" size="small" type="info">
                                            {{ $t("pluginPage.header.enterpriseEdition") }}
                                        </KsTag>
                                    </div>
                                </div>
                                <p v-if="shortDescription" class="plugin-header__subtitle">
                                    {{ shortDescription }}
                                </p>
                            </div>
                        </div>
                        <KsMarkdown
                            v-if="longDescription"
                            class="plugin-header__long"
                            :content="longDescription"
                        />
                    </header>

                    <div :class="['plugin-detail__body', {'plugin-detail__body--flat': flat}]">
                        <slot />
                    </div>
                </div>
                <aside v-if="hasOverviewContent" class="plugin-detail__aside">
                    <PluginOverview
                        :versions="enrichedVersions"
                        :currentVersion
                        :isLatest="isLatestVersion"
                        :canSwitch="canSwitchVersion"
                        :releaseNotesUrl
                        :repoUrl
                        :categories="pluginCategories"
                        :createdBy
                        :managedBy
                        :currentVersionDate
                        :minKestraVersion
                        @select-version="onSelectVersion"
                    />
                </aside>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, watch} from "vue"
    import semver from "semver"
    import {useRoute, useRouter} from "vue-router"
    import {KsIconButton, KsMarkdown, KsTag, KsTaskIcon, type KsBreadcrumbItem} from "@kestra-io/design-system"
    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue"
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
    import PluginToc from "./PluginToc.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import PluginOverview, {type VersionItem} from "./PluginOverview.vue"
    import {usePluginsStore} from "../../stores/plugins"
    import {usePluginsEnrichmentStore} from "../../stores/pluginsEnrichment"
    import {getPluginReleaseUrl, isEntryAPluginElementPredicate, type Plugin, type PluginAuthor, type PluginIconMap} from "../../utils/pluginUtils"

    const KESTRA_TEAM: PluginAuthor = {name: "Kestra Core Team"}

    type RouteTarget = string | Record<string, unknown>

    withDefaults(defineProps<{
        title: string
        breadcrumb?: KsBreadcrumbItem[]
        sidebarPlugins?: Plugin[]
        headerTitle?: string
        headerIconCls?: string
        icons?: PluginIconMap
        elementTypeLabel?: string | null
        isEnterpriseEdition?: boolean
        shortDescription?: string | null
        longDescription?: string | null
        backTo?: RouteTarget
        flat?: boolean
    }>(), {
        breadcrumb: () => [],
        sidebarPlugins: () => [],
        headerTitle: "",
        headerIconCls: undefined,
        icons: () => ({}),
        elementTypeLabel: null,
        isEnterpriseEdition: false,
        shortDescription: null,
        longDescription: null,
        backTo: () => ({name: "plugins/list"}),
        flat: false,
    })

    defineEmits<{
        "router-change": []
    }>()

    const route = useRoute()
    const router = useRouter()
    const pluginsStore = usePluginsStore()
    const enrichmentStore = usePluginsEnrichmentStore()

    const owningEntity = computed<Plugin | null>(() => {
        const cls = route.params.cls as string | undefined
        if (cls) return pluginsStore.findPluginByCls(cls)
        const name = route.params.name as string | undefined
        const subGroup = route.params.subGroup as string | undefined
        return pluginsStore.findPluginByName(name, subGroup)
    })

    const overviewClsRef = computed<string | undefined>(() => {
        const cls = route.params.cls as string | undefined
        if (cls) return cls
        return owningEntity.value?.subGroup ?? owningEntity.value?.group ?? undefined
    })

    const releaseNotesUrl = computed<string | null>(() => getPluginReleaseUrl(overviewClsRef.value))
    const repoUrl = computed<string | null>(() => {
        const url = releaseNotesUrl.value
        return url ? url.replace(/\/releases\/?$/, "") : null
    })

    const pluginCategories = computed<string[]>(() => owningEntity.value?.categories ?? [])

    const createdBy = computed<PluginAuthor | null>(() => {
        if (!owningEntity.value) return null
        const name = enrichmentStore.getEnrichment(owningEntity.value)?.createdBy
        if (name) return {name}
        return enrichmentStore.loaded ? KESTRA_TEAM : null
    })
    const managedBy = computed<PluginAuthor | null>(() => {
        if (!owningEntity.value) return null
        const name = enrichmentStore.getEnrichment(owningEntity.value)?.managedBy
        if (name) return {name}
        return enrichmentStore.loaded ? KESTRA_TEAM : null
    })

    const sortVersionsDesc = (a: string, b: string): number => {
        const va = semver.coerce(a)?.version
        const vb = semver.coerce(b)?.version
        return va && vb ? semver.rcompare(va, vb) : b.localeCompare(a)
    }

    const routeCls = computed<string | undefined>(() => route.params.cls as string | undefined)

    function firstElementClsOf(plugin: Plugin): string | undefined {
        for (const [key, value] of Object.entries(plugin)) {
            if (isEntryAPluginElementPredicate(key, value) && value.length > 0) {
                return (value.find(el => !el?.deprecated) ?? value[0])?.cls
            }
        }
        return undefined
    }

    const versionProbeCls = computed<string | undefined>(() => {
        if (routeCls.value) return routeCls.value
        const entity = owningEntity.value
        if (!entity) return undefined
        const direct = firstElementClsOf(entity)
        if (direct) return direct
        const child = (pluginsStore.plugins ?? []).find(p => p.name === entity.name && p.subGroup && firstElementClsOf(p))
        return child ? firstElementClsOf(child) : undefined
    })

    const canSwitchVersion = computed<boolean>(() => Boolean(routeCls.value))

    const installedVersions = computed<string[]>(() =>
        versionProbeCls.value ? [...(pluginsStore.versions ?? [])].sort(sortVersionsDesc) : [],
    )

    const publicVersions = computed(() => enrichmentStore.getVersions(versionProbeCls.value))

    const enrichedVersions = computed<VersionItem[]>(() => {
        const installed = new Set(installedVersions.value)
        const byVersion = new Map<string, VersionItem>()
        for (const p of publicVersions.value) {
            byVersion.set(p.version, {
                version: p.version,
                publishedAt: p.publishedAt ?? null,
                minKestraCompatibilityVersion: p.minKestraCompatibilityVersion ?? null,
                releaseNotesUrl: p.releaseNotesUrl ?? null,
                installed: installed.has(p.version),
            })
        }
        for (const v of installedVersions.value) {
            if (!byVersion.has(v)) {
                byVersion.set(v, {version: v, publishedAt: null, minKestraCompatibilityVersion: null, releaseNotesUrl: null, installed: true})
            }
        }
        return [...byVersion.values()].sort((a, b) => sortVersionsDesc(a.version, b.version))
    })

    const currentVersion = computed<string | undefined>(() => {
        const fromRoute = route.params.version as string | undefined
        return fromRoute ?? installedVersions.value[0] ?? publicVersions.value[0]?.version
    })

    const isLatestVersion = computed<boolean>(() => {
        const newestPublished = publicVersions.value[0]?.version
        if (!currentVersion.value) return false
        return newestPublished ? currentVersion.value === newestPublished : currentVersion.value === installedVersions.value[0]
    })

    const currentEnrichedVersion = computed<VersionItem | undefined>(() =>
        enrichedVersions.value.find(v => v.version === currentVersion.value),
    )

    const currentVersionDate = computed<string | null>(() => currentEnrichedVersion.value?.publishedAt ?? null)
    const minKestraVersion = computed<string | null>(() => currentEnrichedVersion.value?.minKestraCompatibilityVersion ?? null)

    const hasOverviewContent = computed<boolean>(() =>
        enrichedVersions.value.length > 0
        || createdBy.value !== null
        || managedBy.value !== null
        || pluginCategories.value.length > 0
        || repoUrl.value !== null,
    )

    function onSelectVersion(version: string) {
        const cls = route.params.cls as string | undefined
        if (!cls) return
        router.push({name: "plugins/view", params: {cls, version}})
    }

    watch(
        versionProbeCls,
        (cls) => {
            if (!cls) return
            pluginsStore.loadVersions({cls}).catch((err) => {
                console.error("Failed to load plugin versions", cls, err)
            })
            enrichmentStore.fetchVersions(cls)
        },
        {immediate: true},
    )

    onMounted(() => {
        enrichmentStore.fetchEnrichment()
    })
</script>

<style scoped lang="scss">
    .plugin-header {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        min-width: 0;
        padding: var(--ks-spacing-6);

        &__row {
            display: flex;
            align-items: flex-start;
            gap: var(--ks-spacing-4);
            min-width: 0;
        }

        &__back {
            flex-shrink: 0;
            margin-top: var(--ks-spacing-2);
        }

        &__logo {
            flex-shrink: 0;
            width: 3.75rem;
            height: 3.75rem;
            padding: var(--ks-spacing-2);
            background-color: var(--ks-bg-tag);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);
        }

        &__body {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-2);
        }

        &__title-row {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-3);
            min-width: 0;
        }

        &__name {
            margin: 0;
            font-size: var(--ks-font-size-xl);
            font-weight: 600;
            color: var(--ks-text-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            min-width: 0;
            flex: 1 1 auto;
        }

        &__tags {
            display: flex;
            gap: var(--ks-spacing-2);
            flex-wrap: wrap;
        }

        &__subtitle {
            margin: 0;
            font-size: var(--ks-font-size-sm);
            line-height: 1.25rem;
            color: var(--ks-text-secondary);
        }
    }

    .plugin-page {
        display: flex;
        height: 100%;
        min-height: 0;
        background-color: var(--ks-bg-body);
        container-type: inline-size;
        container-name: plugin-page;

        &__sidebar {
            flex: 0 0 14rem;
            min-width: 0;
            height: 100%;
            overflow: hidden;
            border-right: 1px solid var(--ks-border-default);
        }

        &__body {
            flex: 1 1 auto;
            min-width: 0;
            overflow-y: auto;
            container-type: inline-size;
            container-name: plugin-body;
        }
    }

    .plugin-detail {
        display: flex;
        align-items: flex-start;
        gap: var(--ks-spacing-6);
        padding: var(--ks-spacing-6);

        &__main {
            flex: 1 1 auto;
            min-width: 0;
            display: flex;
            flex-direction: column;
            background-color: var(--ks-bg-surface);
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);

            &--flat {
                background-color: transparent;
                border: none;
                border-radius: 0;
                gap: var(--ks-spacing-4);

                .plugin-header {
                    background-color: var(--ks-bg-surface);
                    border: 1px solid var(--ks-border-default);
                    border-radius: var(--ks-radius-base);
                }
            }
        }

        &__body {
            padding: var(--ks-spacing-6);

            &--flat {
                padding: 0;
            }
        }

        &__aside {
            flex: 0 0 18rem;
            min-width: 0;
        }
    }

    @container plugin-body (max-width: 900px) {
        .plugin-detail__aside {
            display: none;
        }
    }

    @container plugin-page (max-width: 650px) {
        .plugin-page__sidebar {
            display: none;
        }

        .plugin-header {
            gap: var(--ks-spacing-3);

            &__logo {
                width: 2.5rem;
                height: 2.5rem;
            }

            &__name {
                font-size: var(--ks-font-size-lg);
            }

            &__title-row {
                flex-wrap: wrap;
            }
        }

        .plugin-detail {
            padding: var(--ks-spacing-3);
            gap: var(--ks-spacing-3);

            &__body:not(&__body--flat) {
                padding: var(--ks-spacing-4);
            }
        }

        .plugin-header {
            padding: var(--ks-spacing-4);
        }
    }
</style>
