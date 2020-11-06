<template>
    <sidebar-menu
        :menu="menu"
        @toggle-collapse="onToggleCollapse"
        width="200px"
        :collapsed="collapsed"
    >
        <div class="logo" slot="header">
            <router-link :to="{name: 'home'}">
                <span class="img" />
            </router-link>
            <span class="version">{{ version.version }}</span>
        </div>
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
    import Graph from "vue-material-design-icons/Graph";
    import Cog from "vue-material-design-icons/Cog";
    import TimelineClock from "vue-material-design-icons/TimelineClock";
    import BookOpen from "vue-material-design-icons/BookOpen";
    import CardText from "vue-material-design-icons/CardText";
    import HexagonMultiple from "vue-material-design-icons/HexagonMultiple";
    import ChartTimeline from "vue-material-design-icons/ChartTimeline";
    import {mapState} from "vuex";

    Vue.component("graph", Graph);
    Vue.component("settings", Cog);
    Vue.component("timelineclock", TimelineClock);
    Vue.component("bookopen", BookOpen);
    Vue.component("cardtext", CardText);
    Vue.component("hexagon-multiple", HexagonMultiple);
    Vue.component("charttimeline", ChartTimeline);

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
                            element: "graph",
                            class: "menu-icon"
                        }
                    },
                    {
                        href: "/templates",
                        alias: [
                            "/templates*"
                        ],
                        title: this.$t("templates"),
                        icon: {
                            element: "cardtext",
                            class: "menu-icon"
                        }
                    },
                    {
                        href: "/executions",
                        alias: [
                            "/executions*"
                        ],
                        title: this.$t("executions"),
                        icon: {
                            element: "timelineclock",
                            class: "menu-icon"
                        }
                    },
                    {
                        href: "/taskruns",
                        alias: ["/taskruns*"],
                        title: this.$t("taskruns"),
                        icon: {
                            element: "charttimeline",
                            class: "menu-icon"
                        }
                    },
                    {
                        href: "/logs",
                        alias: [
                            "/logs*"
                        ],
                        title: this.$t("logs"),
                        icon: {
                            element: "hexagon-multiple",
                            class: "menu-icon"
                        }
                    },
                    {
                        href: "/plugins",
                        alias: [
                            "/plugins*"
                        ],
                        title: this.$t("plugins.documentation"),
                        icon: {
                            element: "bookopen",
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
                            element: "settings",
                            class: "menu-icon"
                        }
                    }
                ];
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import "src/styles/variable";

    .logo {
        height: 82px;
        min-height: 82px;
        overflow: hidden;

        a {
            display: block;
            height: 52px;
            overflow: hidden;
            border-bottom: 4px solid $tertiary;

            span.img {
                height: 50px;
                background: url(../../../src/assets/logo-white.svg) 0 0 no-repeat;
                background-size: 190px 60px;
                background-position-y: -6px;
                background-position-x: -2px;
                display: block;
            }
        }

        span.version {
            margin-top: 2px;
            font-size: $font-size-xs;
            text-align: right;
            display: block;
            border-top: 2px solid $secondary;
            color: $gray-600;
            border-bottom: 1px solid $gray-900;
            padding-right: 16px;
        }
    }

    .vsm_collapsed {
        span.version {
            color: black;
        }
    }

    /deep/ .vsm--item {
        transition: opacity 0.2s;
    }

    /deep/ .menu-icon {
        font-size: 1.5em;
        background-color: $dark !important;
        padding-bottom: 15px;

        svg {
            top: 3px;
            left: 3px;
        }
    }
</style>
