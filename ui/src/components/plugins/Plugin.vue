<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo?.breadcrumb" />
    <template v-if="!pluginIsSelected">
        <PluginHome v-if="filteredPlugins" :plugins="filteredPlugins" />
    </template>
    <DocsLayout v-else>
        <template #menu>
            <Toc @router-change="onRouterChange" v-if="pluginsStore.plugins" :plugins="pluginsStore.plugins.filter(p => !p.subGroup)" />
        </template>
        <template #content>
            <div class="plugin-doc">
                <div class="d-flex align-items-center justify-content-between gap-3">
                    <div class="d-flex gap-3 mb-3 align-items-center">
                        <TaskIcon
                            class="plugin-icon"
                            :cls="pluginType"
                            onlyIcon
                            :icons="pluginsStore.icons"
                        />
                        <h4 class="mb-0">
                            {{ pluginName }}
                        </h4>
                        <el-button
                            v-if="releaseNotesUrl"
                            size="small"
                            class="release-notes-btn"
                            :icon="GitHub"
                            @click="openReleaseNotes"
                        >
                            {{ $t('plugins.release') }}
                        </el-button>
                    </div>

                    <div class="mb-3 versions" v-if="(pluginsStore.versions?.length ?? 0) > 0">
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
                    </div>
                </div>
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
    const hash = ref<string | undefined>(undefined);

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

    const pluginName = computed(() => {
        const split = pluginType.value?.split(".");
        return split ? split[split.length - 1] : undefined;
    });

    const releaseNotesUrl = computed(() => getPluginReleaseUrl(pluginType.value));

    const pluginIsSelected = computed(
        () => pluginType.value !== undefined && pluginsStore.plugin !== undefined
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

        const clsParam = (route.params as Record<string, any>).cls as string | undefined;
        if (!clsParam) {
            return;
        }

        const loadParams = {
            ...(route.params as Record<string, any>),
            hash: hash.value,
            cls: clsParam,
        };

        isLoading.value = true;
        try {
            await Promise.all([
                pluginsStore.load(loadParams as any),
                pluginsStore.loadVersions(loadParams as any).then((data: any) => {
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

    onMounted(async () => {
        const config = await miscStore.loadConfigs();
        hash.value = config?.pluginsHash;
        loadToc();
        loadPlugin();
    });
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";

    .versions {
        min-width: 200px;
    }

    :deep(.main-container) {
        background: var(--ks-background-panel);
        margin: 0;
        padding: 1rem;
    }
</style>
