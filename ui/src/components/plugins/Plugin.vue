<template>
    <div>
        <b-row>
            <b-col md="9" class="markdown">
                <markdown v-if="plugin" :source="plugin.markdown" :permalink="true" />
                <div v-else>
                    <b-alert variant="info" show>
                        {{ $t('plugins.please') }}
                    </b-alert>
                </div>
            </b-col>
            <b-col md="3">
                <Toc @routerChange="routerChange" v-if="plugins" :plugins="plugins" />
            </b-col>
        </b-row>
    </div>
</template>

<script>
    import RouteContext from "../../mixins/routeContext";
    import Markdown from "../layout/Markdown.vue"
    import Toc from "./Toc.vue"
    import {mapState} from "vuex";

    export default {
        mixins: [RouteContext],
        components: {
            Markdown,
            Toc
        },
        computed: {
            ...mapState("plugin", ["plugin", "plugins"]),
            routeInfo() {
                return {
                    title: this.$route.params.cls ? this.$route.params.cls : this.$t("plugins.documentation"),
                    breadcrumb: [
                        {
                            label: this.$t("plugins.names"),
                            link: {
                                name: "plugins/list"
                            }
                        }
                    ]
                }
            }
        },

        created() {
            this.loadToc();
            this.loadPlugin()
        },

        methods: {
            loadToc() {
                this.$store.dispatch("plugin/list")
            },

            loadPlugin() {
                if (this.$route.params.cls) {
                    this.$store.dispatch(
                        "plugin/load",
                        this.$route.params
                    )
                }
            },

            routerChange() {
                window.scroll({
                    top: 0,
                    behavior: "smooth"
                })

                this.loadPlugin();
            }
        }
    };
</script>

<style lang="scss" >
    @import "../../styles/_variable.scss";
    .markdown {
        h1 {
            font-size: $h2-font-size;
        }

        blockquote {
            margin-top: 0;
        }

        mark {
            background: $success;
            color: $white;
            font-size: $font-size-sm;
            padding: 2px 8px 2px 8px;

            * {
                color: $white !important;
            }
        }

        h2 {
            margin-top: $spacer * 2;
            border-bottom: 1px solid $gray-500;
            font-weight: bold;
            color: $gray-700
        }

        h3 {
            code {
                display: inline-block;
                font-size: $font-size-base * 1.10;
                font-weight: 400;
            }
        }

        h4 {
            code {
                display: inline-block;
                font-size: $font-size-base * 1.00;
                font-weight: 400;
            }
        }
    }

</style>
