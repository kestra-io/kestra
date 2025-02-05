<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo?.breadcrumb" />
    <template v-if="!pluginIsSelected">
        <plugin-home v-if="plugins" :plugins="plugins" />
    </template>
    <docs-layout v-else>
        <template #menu>
            <Toc @router-change="onRouterChange" v-if="plugins" :plugins="plugins.filter(p => !p.subGroup)" />
        </template>
        <template #content>
            <div class="plugin-doc">
                <div class="d-flex gap-3 mb-3 align-items-center">
                    <task-icon
                        class="plugin-icon"
                        :cls="$route.params.cls"
                        only-icon
                        :icons="icons"
                    />
                    <h4 class="mb-0">
                        {{ pluginName }}
                    </h4>
                </div>
                <Suspense v-loading="isLoading">
                    <schema-to-html class="plugin-schema" :dark-mode="Utils.getTheme() === 'dark'" :schema="plugin.schema" :props-initially-expanded="true" :plugin-type="$route.params.cls">
                        <template #markdown="{content}">
                            <markdown font-size-var="font-size-base" :source="content" />
                        </template>
                    </schema-to-html>
                </Suspense>
            </div>
        </template>
    </docs-layout>
</template>

<script setup>
    import Utils from "../../utils/utils.js";
    import {TaskIcon} from "@kestra-io/ui-libs";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import Markdown from "../layout/Markdown.vue"
    import Toc from "./Toc.vue"
    import {mapState} from "vuex";
    import PluginHome from "./PluginHome.vue";
    import DocsLayout from "../docs/DocsLayout.vue";
    import {SchemaToHtml} from "@kestra-io/ui-libs";

    export default {
        mixins: [RouteContext],
        components: {
            SchemaToHtml,
            DocsLayout,
            PluginHome,
            Markdown,
            Toc,
            TopNavBar
        },
        computed: {
            ...mapState("plugin", ["plugin", "plugins", "icons"]),
            routeInfo() {
                return {
                    title: this.$route.params.cls ? this.$route.params.cls : this.$t("plugins.names"),
                    breadcrumb: !this.$route.params.cls ? undefined : [
                        {
                            label: this.$t("plugins.names"),
                            link: {
                                name: "plugins/list"
                            }
                        }
                    ]
                }
            },
            pluginName() {
                const split = this.$route.params.cls.split(".");
                return split[split.length - 1];
            },
            pluginIsSelected() {
                return this.plugin && this.$route.params.cls
            }
        },
        data() {
            return {
                isLoading: false
            };
        },
        created() {
            this.loadToc();
            this.loadPlugin()
        },
        watch: {
            $route(newValue, _oldValue) {
                if (newValue.name.startsWith("plugins/")) {
                    this.onRouterChange();
                }
            }
        },
        methods: {
            loadToc() {
                this.$store.dispatch("plugin/listWithSubgroup", {
                    includeDeprecated: false
                })
            },

            loadPlugin() {
                if (this.$route.params.cls) {
                    this.isLoading = true;

                    this.$store
                        .dispatch("plugin/load", this.$route.params)
                        .finally(() => {
                            this.isLoading = false
                        });
                }
            },

            onRouterChange() {
                window.scroll({
                    top: 0,
                    behavior: "smooth"
                })

                this.loadPlugin();
            }
        }
    };
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";
</style>