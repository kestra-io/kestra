<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo?.breadcrumb" />
    <template v-if="isPluginList">
        <PluginHome v-if="filteredPlugins" :plugins="filteredPlugins" />
    </template>
    <DocsLayout v-else>
        <template #secondary-header>
            <div class="plugin-secondary-header">
                <div class="d-flex align-items-center gap-3">
                    <TaskIcon
                        class="plugin-icon"
                        :cls="pluginType"
                        onlyIcon
                        :icons="pluginsStore.icons"
                    />
                    <h4 class="mb-0 plugin-name">
                        {{ pluginName }}
                    </h4>
                    <el-button
                        v-if="releaseNotesUrl"
                        size="small"
                        class="release-notes-btn d-none d-md-inline-flex"
                        :icon="GitHub"
                        @click="openReleaseNotes"
                    >
                        {{ $t('plugins.release') }}
                    </el-button>
                </div>
                <div class="versions" v-if="(pluginsStore.versions?.length ?? 0) > 0">
                    <el-select
                        v-model="version"
                        placeholder="Version"
                        size="small"
                        :disabled="(pluginsStore.versions?.length ?? 0) === 1"
                        @change="selectVersion(version)"
                    >
                        <template #label="{value}">
                            <span>Version: </span>
                            <span style="font-weight: bold">{{ value }}</span>
                        </template>
                        <el-option
                            v-for="item in pluginsStore.versions"
                            :key="item"
                            :label="item"
                            :value="item"
                        />
                    </el-select>
                    <div class="release-notes-mobile d-inline-flex d-md-none" v-if="releaseNotesUrl">
                        <el-button
                            size="small"
                            class="release-notes-btn"
                            :icon="GitHub"
                            @click="openReleaseNotes"
                        >
                            {{ $t('plugins.release') }}
                        </el-button>
                    </div>
                </div>
            </div>
        </template>
        <template #menu>
            <Toc @router-change="onRouterChange" v-if="pluginsStore.plugins" :plugins="pluginsStore.plugins.filter(p => !p.subGroup)" />
        </template>
        <template #content>
            <div class="plugin-doc">
                <Suspense v-loading="isLoading">
                    <SchemaToHtml
                        class="plugin-schema"
                        :darkMode="miscStore.theme === 'dark'"
                        :schema="pluginsStore.plugin!.schema"
                        :propsInitiallyExpanded="true"
                        :pluginType="pluginType!"
                        noUrlChange
                    >
                        <template #markdown="{content}">
                            <Markdown font-size-var="font-size-base" :source="content" />
                        </template>
                    </SchemaToHtml>
                </Suspense>
            </div>
        </template>
    </DocsLayout>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useI18n} from "vue-i18n";
    import {TaskIcon, SchemaToHtml} from "@kestra-io/ui-libs";
    import DocsLayout from "../docs/DocsLayout.vue";
    import PluginHome from "./PluginHome.vue";
    import Markdown from "../layout/Markdown.vue";
    import Toc from "./Toc.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import GitHub from "vue-material-design-icons/Github.vue";
    import {usePluginsStore} from "../../stores/plugins";
    import {useMiscStore} from "override/stores/misc";
    import {getPluginReleaseUrl} from "../../utils/pluginUtils";


    const pluginsStore = usePluginsStore();
    const miscStore = useMiscStore();

    const route = useRoute();
    const router = useRouter();

    const {t} = useI18n();

    const isLoading = ref<boolean>(false);
    const version = ref<string | undefined>(undefined);
    const pluginType = ref<string | undefined>(undefined);
    const filteredPlugins = ref<any[] | undefined>(undefined);

    const routeInfo = computed(() => ({
        title: pluginType.value ?? t("plugins.names"),
        breadcrumb:
            pluginType.value === undefined
                ? undefined
                : [
                    {
                        label: t("plugins.names"),
                        link: {name: "plugins/list"},
                    },
                ],
    }));

    const hash = computed(() => miscStore.configs?.pluginsHash ?? 0);

    const pluginName = computed(() => {
        const split = pluginType.value?.split(".");
        return split ? split[split.length - 1] : undefined;
    });

    const releaseNotesUrl = computed(() => getPluginReleaseUrl(pluginType.value));


    const isPluginList = computed(
        () => typeof route.name === "string" && route.name === "plugins/list"
    );

    function loadToc() {
        pluginsStore.listWithSubgroup({includeDeprecated: false});
    }

    function selectVersion(ver: string | undefined) {
        router.push({
            name: "plugins/view",
            params: {cls: pluginType.value, version: ver},
        });
    }

    async function loadPlugin() {
        if (route.params.version) {
            version.value = route.params.version as string;
        }

        const clsParam = route.params.cls as string | undefined;
        if (!clsParam) {
            return;
        }

        const loadParams = {
            version: version.value,
            hash: hash.value,
            cls: clsParam,
        };

        isLoading.value = true;
        try {
            await Promise.all([
                pluginsStore.load(loadParams),
                pluginsStore.loadVersions(loadParams).then((data) => {
                    if (data.versions?.length > 0) {
                        if (!version.value) version.value = data.versions[0];
                    }
                }),
            ]);
        } finally {
            isLoading.value = false;
            pluginType.value = clsParam;
        }
    }

    function onRouterChange() {
        window.scroll({top: 0, behavior: "smooth"});
        loadPlugin();
    }

    function openReleaseNotes() {
        if (releaseNotesUrl.value) {
            window.open(releaseNotesUrl.value, "_blank");
        }
    }

    watch(
        [() => route.name, () => route.params],
        ([newName]) => {
            if (newName === "plugins/list") {
                pluginType.value = undefined;
                version.value = undefined;
            }
            if (typeof newName === "string" && newName.startsWith("plugins/")) {
                onRouterChange();
            }
        },
        {immediate: true}
    );


    watch(
        () => pluginsStore.plugins,
        async () => {
            filteredPlugins.value = await pluginsStore.filteredPlugins([
                "apps",
                "appBlocks",
                "charts",
                "dataFilters",
                "dataFiltersKPI",
            ]);
        },
        {immediate: true}
    );

    onMounted(() => {
        miscStore.loadConfigs();
        loadToc();
        loadPlugin();
    });
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";

    .plugin-secondary-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding: 2rem;
        padding-bottom: 0;
        background-color: var(--ks-background-panel);
        flex: 1;
        min-height: 64px;
        
        .plugin-icon {
            width: 35px;
            height: 35px;
            flex-shrink: 0;
        }
        
        .plugin-name {
            font-size: 1.5rem;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            flex: 1;
            min-width: 0;
        }
        
        .release-notes-btn {
            background-color: var(--ks-background-info);
            color: var(--ks-content-info);
            border: 1px solid var(--ks-border-info);
            font-family: 'Courier New', Courier, monospace;
            white-space: nowrap;
            flex-shrink: 0;
            
            :deep(.material-design-icon) {
                position: absolute;
                bottom: 0;
            }
        }
    }

    .versions {
        min-width: 180px;
    }

    :deep(.main-container) {
        background: var(--ks-background-panel);
        margin: 0;
        padding: 0;
    }
    
    .plugin-doc {
        background-color: var(--ks-background-panel);
    }
    
    @media (max-width: 991px) {
        .plugin-secondary-header {
            flex-wrap: wrap;
            padding: 0.5rem 0.75rem;
            gap: 0.5rem;
            
            .plugin-icon {
                width: 32px;
                height: 32px;
                margin-right: 0.5rem;
            }
            
            .plugin-name {
                font-size: 1.25rem;
                flex: 1;
                min-width: 0;
            }
            
            .release-notes-btn {
                padding: 6px 12px;
                font-size: 0.75rem;
                min-width: auto;
            }
            
            .versions {
                width: 100%;
                display: flex;
                flex-direction: column;
                align-items: stretch;
                gap: 0.5rem;
            }

            .versions :deep(.el-select) {
                width: 100%;
            }

            .versions .release-notes-mobile {
                width: 100%;
                display: flex;
                justify-content: flex-start;
                margin-top: 0;
            }

            .versions .release-notes-mobile .release-notes-btn {
                width: 100%;
                justify-content: center;
            }
        }
        
        .plugin-doc {
            padding: 0.75rem;
        }
    }
</style>
