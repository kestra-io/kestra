<template>
    <div class="plugin-doc">
        <template v-if="fetchPluginDocumentation && currentPlugin">
            <div class="d-flex gap-3 mb-3 align-items-center">
                <KsTaskIcon
                    class="plugin-icon"
                    :cls="currentPlugin.cls"
                    onlyIcon
                    :icons="pluginsStore.icons"
                />
                <h4 class="mb-0 plugin-title text-truncate">
                    {{ pluginName }}
                </h4>
                <KsButton
                    v-if="releaseNotesUrl"
                    size="small"
                    class="release-notes-btn"
                    :icon="GitHub"
                    @click="openReleaseNotes"
                >
                    {{ $t('plugins.release') }}
                </KsButton>
            </div>
            <Suspense>
                <SchemaToHtml
                    class="plugin-schema"
                    :darkMode="isDarkTheme"
                    :schema="currentPlugin?.schema"
                    :pluginType="currentPlugin?.cls"
                    :forceIncludeProperties="pluginsStore.forceIncludeProperties"
                    noUrlChange
                >
                    <template #markdown="{content}">
                        <!-- Plugin schema content: search disabled -->
                        <KsMarkdown
                            :content="content"
                        />
                    </template>
                </SchemaToHtml>
            </Suspense>
        </template>

        <KsMarkdown
            v-else-if="introContent"
            :content="introContent"
            :class="{'position-absolute': absolute}"
        />
    </div>
</template>

<script setup lang="ts">

    import {computed} from "vue"
    import SchemaToHtml from "./schema/SchemaToHtml.vue"
    import {KsTaskIcon, KsMarkdown} from "@kestra-io/design-system"
    import {getPluginReleaseUrl} from "../../utils/pluginUtils"
    import {getTheme} from "../../utils/utils"
    import {useMiscStore} from "override/stores/misc"
    import {usePluginsStore} from "../../stores/plugins"
    import GitHub from "vue-material-design-icons/Github.vue"
    import intro from "../../assets/docs/basic.md?raw"

    const props = withDefaults(defineProps<{
        overrideIntro?: string | null;
        absolute?: boolean;
        fetchPluginDocumentation?: boolean;
        plugin?: any;
    }>(), {
        overrideIntro: null,
        absolute: false,
        fetchPluginDocumentation: true,
        plugin: null,
    })

    const miscStore = useMiscStore()
    const pluginsStore = usePluginsStore()

    const isDarkTheme = computed(() => {
        void miscStore.theme
        return getTheme() === "dark"
    })

    const currentPlugin = computed(() => {
        return props.plugin ?? pluginsStore.editorPlugin
    })

    const introContent = computed(() => {
        return props.overrideIntro ?? intro
    })

    const pluginName = computed(() => {
        const split = currentPlugin.value?.cls.split(".")
        return split[split.length - 1]
    })

    const releaseNotesUrl = computed(() => {
        return getPluginReleaseUrl(currentPlugin.value?.cls)
    })

    const openReleaseNotes = () => {
        if (releaseNotesUrl.value) {
            window.open(releaseNotesUrl.value, "_blank")
        }
    }
</script>

<style scoped lang="scss">
    .plugin-icon {
        width: 25px;
        height: 25px;
        min-width: 25px;
        min-height: 25px;
    }

    .plugin-title {
        min-width: 50px;
        font-size: var(--ks-font-size-lg);
    }

    .release-notes-btn {
        background-color: var(--ks-bg-info);
        color: var(--ks-status-info);
        border: 1px solid var(--ks-border-info);
        white-space: nowrap;

        :deep(.material-design-icon) {
            position: absolute;
            bottom: 0;
        }

        @media (max-width: 576px) {
            padding: 6px 12px;
            font-size: var(--ks-font-size-sm);
            min-width: auto;
        }
    }
</style>
