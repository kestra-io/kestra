<template>
    <div class="plugin-doc">
        <template v-if="editorPlugin">
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
            </div>
            <Suspense>
                <schema-to-html class="plugin-schema" :dark-mode="Utils.getTheme() === 'dark'" :schema="editorPlugin.schema" :plugin-type="editorPlugin.cls">
                    <template #markdown="{content}">
                        <markdown font-size-var="font-size-base" :source="content" />
                    </template>
                </schema-to-html>
            </Suspense>
        </template>
        <markdown v-else :source="introContent" />
    </div>
</template>

<script setup>
    import Utils from "../../utils/utils.js";
</script>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {SchemaToHtml, TaskIcon} from "@kestra-io/ui-libs";
    import {mapState} from "vuex";
    import intro from "../../assets/documentations/basic.md?raw"

    export default {
        components: {Markdown, SchemaToHtml, TaskIcon},
        props: {
            overrideIntro: {
                type: String,
                default: null
            }
        },
        computed: {
            ...mapState("plugin", ["editorPlugin", "icons"]),
            introContent () {
                return this.overrideIntro ?? intro
            },
            pluginName() {
                const split = this.editorPlugin.cls.split(".");
                return split[split.length - 1];
            }
        },
        created() {
            this.$store.dispatch("plugin/list");
        }
    }
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";
</style>