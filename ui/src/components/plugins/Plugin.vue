<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <template v-if="!pluginIsSelected">
        <plugin-home v-if="plugins" :plugins="plugins" />
    </template>
    <section v-else class="container">
        <el-row :gutter="15">
            <el-col :span="4">
                <Toc @router-change="onRouterChange" v-if="plugins" :plugins="plugins.filter(p => !p.subGroup)" />
            </el-col>
            <el-col :offset="1" :span="19" class="markdown" v-loading="isLoading">
                <markdown :source="plugin.markdown" :permalink="true" />
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
                this.$store.dispatch("plugin/listWithSubgroup")
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

<style lang="scss" scoped>
    section {
        overflow-x: hidden;
    }
</style>
