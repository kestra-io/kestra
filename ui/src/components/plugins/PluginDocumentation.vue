<template>
    <div class="plugin-doc">
        <template v-if="fetchPluginDocumentation && pluginsStore.editorPlugin">
            <div class="d-flex gap-3 mb-3 align-items-center">
                <TaskIcon
                    class="plugin-icon"
                    :cls="pluginsStore.editorPlugin.cls"
                    onlyIcon
                    :icons="pluginsStore.icons"
                />
                <h4 class="mb-0 plugin-title text-truncate">
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
            <Suspense>
                <SchemaToHtml
                    class="plugin-schema"
                    :darkMode="miscStore.theme === 'dark'"
                    :schema="pluginsStore.editorPlugin.schema"
                    :pluginType="pluginsStore.editorPlugin.cls"
                    :forceIncludeProperties="pluginsStore.forceIncludeProperties"
                    noUrlChange
                >
                    <template #markdown="{content}">
                        <Markdown font-size-var="font-size-base" :source="content" />
                    </template>
                </SchemaToHtml>
            </Suspense>
        </template>
        <Markdown v-else :source="introContent" :class="{'position-absolute': absolute}" />
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted} from "vue";
    import Markdown from "../layout/Markdown.vue";
    import {SchemaToHtml, TaskIcon} from "@kestra-io/ui-libs";
    import GitHub from "vue-material-design-icons/Github.vue";
    import intro from "../../assets/docs/basic.md?raw";
    import {getPluginReleaseUrl} from "../../utils/pluginUtils";
    import {usePluginsStore} from "../../stores/plugins";
    import {useMiscStore} from "override/stores/misc";

    const props = withDefaults(defineProps<{
        overrideIntro?: string | null;
        absolute?: boolean;
        fetchPluginDocumentation?: boolean;
    }>(), {
        overrideIntro: null,
        absolute: false,
        fetchPluginDocumentation: true
    });

    const pluginsStore = usePluginsStore();
    const miscStore = useMiscStore();

    const introContent = computed(() => props.overrideIntro ?? intro);

    const pluginName = computed(() => {
        if (!pluginsStore.editorPlugin?.cls) return "";
        const split = pluginsStore.editorPlugin.cls.split(".");
        return split[split.length - 1];
    });

    const releaseNotesUrl = computed(() =>
        pluginsStore.editorPlugin?.cls ? getPluginReleaseUrl(pluginsStore.editorPlugin.cls) : null
    );

    function openReleaseNotes() {
        if (releaseNotesUrl.value) {
            window.open(releaseNotesUrl.value, "_blank");
        }
    }

    onMounted(() => {
        pluginsStore.list();
    });
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";
</style>
