<template>
    <sidebar-menu
        :menu="menu"
        @toggle-collapse="onToggleCollapse"
        width="268px"
        :collapsed="collapsed"
    >
        <div class="logo" slot="header">
            <router-link :to="{name: 'home'}">
                <span class="img" />
            </router-link>
        </div>

        <span slot="footer">
            <span class="version">{{ version ? version.version : '' }}</span>
        </span>

        <span slot="toggle-icon">
            <chevron-right v-if="collapsed" />
            <chevron-left v-else />
        </span>
    </sidebar-menu>
</template>

<script>
    import Vue from "vue";
    import {SidebarMenu} from "vue-sidebar-menu";
    import ChevronLeft from "vue-material-design-icons/ChevronLeft";
    import ChevronRight from "vue-material-design-icons/ChevronRight";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline";
    import ContentCopy from "vue-material-design-icons/ContentCopy";
    import TimelineClockOutline from "vue-material-design-icons/TimelineClockOutline";
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline";
    import NotebookOutline from "vue-material-design-icons/NotebookOutline";
    import BookOutline from "vue-material-design-icons/BookOutline";
    import CogOutline from "vue-material-design-icons/CogOutline";
    import {mapState} from "vuex";

    Vue.component("FlowMenuIcon", FileTreeOutline);
    Vue.component("TemplateMenuIcon", ContentCopy);
    Vue.component("ExecutionMenuIcon", TimelineClockOutline);
    Vue.component("TaskRunMenuIcon", TimelineTextOutline);
    Vue.component("LogMenuIcon", NotebookOutline);
    Vue.component("DocumentationMenuIcon", BookOutline);
    Vue.component("SettingMenuIcon", CogOutline);

    export default {
        components: {
            ChevronLeft,
            ChevronRight,
            SidebarMenu,
        },
        methods: {
            onToggleCollapse(folded) {
                this.collapsed = folded;
                localStorage.setItem("menuCollapsed", folded ? "true" : "false");
                this.$emit("onMenuCollapse", folded);
            }
        },
        created() {
            this.$store.dispatch("misc/loadVersion")
        },
        data() {
            return {
                collapsed: localStorage.getItem("menuCollapsed") === "true"
            };
        },
        mounted() {
            this.$el.querySelectorAll(".vsm--item span").forEach(e => {
                //empty icon name on mouseover
                e.setAttribute("title","")
            })
        },
        computed: {
            ...mapState("misc", ["version"]),
            menu() {
                return [
                    {
                        href: "/flows",
                        alias: [
                            "/flows*"
                        ],
                        title: this.$t("flows"),
                        icon: {
                            element: "FlowMenuIcon",
                            class: "menu-icon",
                        },
                    },
                    {
                        href: "/templates",
                        alias: [
                            "/templates*"
                        ],
                        title: this.$t("templates"),
                        icon: {
                            element: "TemplateMenuIcon",
                            class: "menu-icon",
                        },
                    },
                    {
                        href: "/executions",
                        alias: [
                            "/executions*"
                        ],
                        title: this.$t("executions"),
                        icon: {
                            element: "ExecutionMenuIcon",
                            class: "menu-icon"
                        },
                    },
                    {
                        href: "/taskruns",
                        alias: ["/taskruns*"],
                        title: this.$t("taskruns"),
                        icon: {
                            element: "TaskRunMenuIcon",
                            class: "menu-icon"
                        },
                    },
                    {
                        href: "/logs",
                        alias: [
                            "/logs*"
                        ],
                        title: this.$t("logs"),
                        icon: {
                            element: "LogMenuIcon",
                            class: "menu-icon"
                        },
                    },
                    {
                        href: "/plugins",
                        alias: [
                            "/plugins*"
                        ],
                        title: this.$t("plugins.documentation"),
                        icon: {
                            element: "DocumentationMenuIcon",
                            class: "menu-icon"
                        }
                    },
                    {
                        href: "/settings",
                        alias: [
                            "/settings*"
                        ],
                        title: this.$t("settings"),
                        icon: {
                            element: "SettingMenuIcon",
                            class: "menu-icon"
                        }
                    }
                ];
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import "../../styles/variable";

    .logo {
        overflow: hidden;
        padding: 35px 0;
        height: 133px;
        position: relative;
        a {
            transition: 0.3s all;
            position: absolute;
            left: 37px;
            display: block;
            height: 55px;
            width: 100%;
            overflow: hidden;

            span.img {
                height: 100%;
                background: url(../../../src/assets/logo.svg) 0 0 no-repeat;
                background-size: 179px 55px;
                display: block;

                .theme-dark & {
                    background: url(../../../src/assets/logo-white.svg) 0 0 no-repeat;
                    background-size: 179px 55px;
                }
            }
        }
    }

    span.version {
        transition: 0.3s all;
        white-space: nowrap;
        font-size: $font-size-xs;
        text-align: center;
        display: block;
        color: var(--tertiary);
    }

    .vsm_collapsed {
        .logo {
            a {
                left: 0;
            }
        }

        span.version {
            opacity: 0;
        }
    }

    /deep/ .vsm--item {
        transition: opacity 0.2s;
    }

    /deep/ .menu-icon {
        font-size: 1.5em;
        background-color: transparent !important;
        padding-bottom: 15px;

        svg {
            top: 3px;
            left: 3px;
        }
    }
</style>
