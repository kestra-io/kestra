<template>
    <div class="plugin-doc">
        <template v-if="fetchPluginDocumentation && editorPlugin">
            <div class="d-flex gap-3 mb-3 align-items-center">
                <task-icon
                    class="plugin-icon"
                    :cls="editorPlugin.cls"
                    only-icon
                    :icons="icons"
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
            <Suspense>
                <schema-to-html class="plugin-schema" :dark-mode="theme === 'dark'" :schema="editorPlugin.schema" :plugin-type="editorPlugin.cls">
                    <template #markdown="{content}">
                        <markdown font-size-var="font-size-base" :source="content" />
                    </template>
                </schema-to-html>
            </Suspense>
        </template>
        <markdown v-else :source="introContent" :class="{'position-absolute': absolute}" />
    </div>
</template>

<script setup>
    import Markdown from "../layout/Markdown.vue";
    import {SchemaToHtml, TaskIcon} from "@kestra-io/ui-libs";
    import GitHub from "vue-material-design-icons/Github.vue";
</script>

<script>
    import {mapState, mapGetters} from "vuex";
    import intro from "../../assets/docs/basic.md?raw";
    import {getPluginReleaseUrl} from "../../utils/pluginUtils";

    export default {
        props: {
            overrideIntro: {
                type: String,
                default: null
            },
            absolute: {
                type: Boolean,
                default: false
            },
            fetchPluginDocumentation: {
                type: Boolean,
                default: true
            }
        },
        computed: {
            ...mapState("plugin", ["editorPlugin", "icons"]),
            ...mapGetters("misc", ["theme"]),
            introContent () {
                return this.overrideIntro ?? intro
            },
            pluginName() {
                const split = this.editorPlugin.cls.split(".");
                return split[split.length - 1];
            },
            releaseNotesUrl() {
                return getPluginReleaseUrl(this.editorPlugin.cls);
            }
        },
        created() {
            this.$store.dispatch("plugin/list");
        },
        methods: {
            openReleaseNotes() {
                if (this.releaseNotesUrl) {
                    window.open(this.releaseNotesUrl, "_blank");
                }
            }
        }
    }
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";
</style>