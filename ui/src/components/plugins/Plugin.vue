<template>
    <top-nav-bar :title="routeInfo.title" />
    <section :class="pluginIsSelected ? 'mt-4': ''">
        <el-row :gutter="15">
            <el-col :span="4" v-if="pluginIsSelected">
                <Toc @router-change="onRouterChange" v-if="plugins" :plugins="plugins" />
            </el-col>
            <el-col :span="(pluginIsSelected) ? 18 : 22" class="markdown" v-loading="isLoading">
                <markdown v-if="pluginIsSelected" :source="plugin.markdown" :permalink="true" />
                <div v-else>
                    <plugin-home v-if="plugins" :plugins="plugins" />
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import Markdown from "../layout/Markdown.vue"
    import Toc from "./Toc.vue"
    import {mapState} from "vuex";
    import PluginHome from "./PluginHome.vue";

    export default {
        mixins: [RouteContext],
        components: {
            PluginHome,
            Markdown,
            Toc,
            TopNavBar
        },
        computed: {
            ...mapState("plugin", ["plugin", "plugins"]),
            routeInfo() {
                return {
                    title: this.$route.params.cls ? this.$route.params.cls : this.$t("plugins.names"),
                    breadcrumb: [
                        {
                            label: this.$t("plugins.names"),
                            link: {
                                name: "plugins/list"
                            }
                        }
                    ]
                }
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
                this.$store.dispatch("plugin/list")
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

<style lang="scss">
    section {
        overflow-x: hidden;
    }
    .markdown {
        h1 {
            font-size: calc(var(--font-size-base) * 2);
        }

        blockquote {
            margin-top: 0;
        }

        mark {
            background: var(--bs-success);
            color: var(--bs-white);
            font-size: var(--font-size-sm);
            padding: 2px 8px 2px 8px;
            border-radius: var(--bs-border-radius-sm);

            * {
                color: var(--bs-white) !important;
            }
        }

        h2 {
            margin-top: calc(var(--spacer) * 2);
            border-bottom: 1px solid var(--bs-gray-500);
            font-weight: bold;
            color: var(--bs-gray-700)
        }

        h3 {
            code {
                display: inline-block;
                font-size: calc(var(--font-size-base) * 1.10);
                font-weight: 400;
            }
        }

        h2, h3 {
            margin-left: -15px;

            .header-anchor {
                opacity: 0;
                transition: all ease 0.2s;
            }

            &:hover {
                .header-anchor {
                    opacity: 1;
                }
            }
        }

        h4 {
            code {
                display: inline-block;
                font-size: calc(var(--font-size-base) * 1.00);
                font-weight: 400;
            }
        }
    }

</style>
