<template>
    <div class="plugin-doc">
        <template v-if="fetchPluginDocumentation && currentPlugin">
            <div class="d-flex gap-3 mb-3 align-items-center">
                <TaskIcon
                    class="plugin-icon"
                    :cls="currentPlugin.cls"
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
                    :schema="currentPlugin?.schema"
                    :pluginType="currentPlugin?.cls"
                    :forceIncludeProperties="pluginsStore.forceIncludeProperties"
                    noUrlChange
                >
                    <template #markdown="{content}">
                        <!-- Plugin schema content: search disabled -->
                        <Markdown 
                            font-size-var="font-size-base"
                            :source="content"
                        />
                    </template>
                </SchemaToHtml>
            </Suspense>
        </template>

        <Markdown
            v-else-if="introContent"
            :source="introContent"
            :class="{'position-absolute': absolute}"
            :showSearch="true"
            :collapseExamples="true"
        />
    </div>
</template>

<script setup lang="ts">

    import {computed} from "vue";
    import Markdown from "../layout/Markdown.vue";
    import {SchemaToHtml, TaskIcon} from "@kestra-io/ui-libs";
    import {getPluginReleaseUrl} from "../../utils/pluginUtils";
    import {useMiscStore} from "override/stores/misc";
    import {usePluginsStore} from "../../stores/plugins";
    import GitHub from "vue-material-design-icons/Github.vue";
    import intro from "../../assets/docs/basic.md?raw";

    const props = withDefaults(defineProps<{
        overrideIntro?: string | null;
        absolute?: boolean;
        fetchPluginDocumentation?: boolean;
        plugin?: any;
    }>(), {
        overrideIntro: null,
        absolute: false,
        fetchPluginDocumentation: true,
        plugin: null
    });

    const miscStore = useMiscStore();
    const pluginsStore = usePluginsStore();

    const currentPlugin = computed(() => {
        return props.plugin ?? pluginsStore.editorPlugin;
    });

    const introContent = computed(() => {
        return props.overrideIntro ?? intro;
    });

    const pluginName = computed(() => {
        const split = currentPlugin.value?.cls.split(".");
        return split[split.length - 1];
    });

    const releaseNotesUrl = computed(() => {
        return getPluginReleaseUrl(currentPlugin.value?.cls);
    });

    const openReleaseNotes = () => {
        if (releaseNotesUrl.value) {
            window.open(releaseNotesUrl.value, "_blank");
        }
    };
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";
</style>
