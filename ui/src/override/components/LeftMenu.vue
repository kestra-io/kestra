<template>
    <sidebar-menu
        id="side-menu"
        :menu="disabledCurrentRoute(menu)"
        @update:collapsed="onToggleCollapse"
        :show-one-child="true"
        width="268px"
        :collapsed="collapsed"
    >
        <template #header>
            <div class="logo">
                <router-link :to="{name: 'home'}">
                    <span class="img" />
                </router-link>
            </div>
        </template>

        <template #footer>
            <span class="version">{{ configs ? configs.version : '' }}</span>
        </template>

        <template #toggle-icon>
            <chevron-right v-if="collapsed" />
            <chevron-left v-else />
        </template>
    </sidebar-menu>
</template>

<script>
    import {shallowRef} from "vue"

    import {SidebarMenu} from "vue-sidebar-menu";
    import ChevronLeft from "vue-material-design-icons/ChevronLeft";
    import ChevronRight from "vue-material-design-icons/ChevronRight";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline";
    import ContentCopy from "vue-material-design-icons/ContentCopy";
    import TimelineClockOutline from "vue-material-design-icons/TimelineClockOutline";
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline";
    import NotebookOutline from "vue-material-design-icons/NotebookOutline";
    import BookMultipleOutline from "vue-material-design-icons/BookMultipleOutline";
    import FileCodeOutline from "vue-material-design-icons/FileCodeOutline";
    import GoogleCirclesExtended from "vue-material-design-icons/GoogleCirclesExtended";
    import Slack from "vue-material-design-icons/Slack";
    import Github from "vue-material-design-icons/Github";
    import CogOutline from "vue-material-design-icons/CogOutline";
    import {mapState} from "vuex";

    export default {
        components: {
            ChevronLeft,
            ChevronRight,
            SidebarMenu,
        },
        emits: ["menu-collapse"],
        methods: {
            onToggleCollapse(folded) {
                this.collapsed = folded;
                localStorage.setItem("menuCollapsed", folded ? "true" : "false");
                this.$emit("menu-collapse", folded);
            },
            disabledCurrentRoute(items) {
                return items
                .map(r => {
                    if (r.href === this.$route.path) {
                        r.disabled = true
                    }

                    if (this.$route.path.startsWith(r.href)) {
                        r.class = "vsm--link_active"
                    }

                    return r;
                })
            },
            generateMenu() {
                return [
                    {
                        href: "/flows",
                        alias: [
                            "/flows*"
                        ],
                        title: this.$t("flows"),
                        icon: {
                            element: shallowRef(FileTreeOutline),
                            class: "menu-icon",
                        },
                        exact: false,
                    },
                    {
                        href: "/templates",
                        alias: [
                            "/templates*"
                        ],
                        title: this.$t("templates"),
                        icon: {
                            element: shallowRef(ContentCopy),
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
                            element: shallowRef(TimelineClockOutline),
                            class: "menu-icon"
                        },
                    },
                    {
                        href: "/taskruns",
                        alias: ["/taskruns*"],
                        title: this.$t("taskruns"),
                        icon: {
                            element: shallowRef(TimelineTextOutline),
                            class: "menu-icon"
                        },
                        hidden: !(this.configs && this.configs.isTaskRunEnabled)
                    },
                    {
                        href: "/logs",
                        alias: [
                            "/logs*"
                        ],
                        title: this.$t("logs"),
                        icon: {
                            element: shallowRef(NotebookOutline),
                            class: "menu-icon"
                        },
                    },
                    {
                        alias: [
                            "/plugins*"
                        ],
                        title: this.$t("documentation.documentation"),
                        icon: {
                            element: shallowRef(BookMultipleOutline),
                            class: "menu-icon"
                        },
                        child: [
                            {
                                href: "https://kestra.io/docs/",
                                title: this.$t("documentation.developer"),
                                icon: {
                                    element: shallowRef(FileCodeOutline),
                                    class: "menu-icon"
                                },
                                external: true
                            },
                            {
                                href: "/plugins",
                                title: this.$t("plugins.names"),
                                icon: {
                                    element: shallowRef(GoogleCirclesExtended),
                                    class: "menu-icon"
                                },
                            },
                            {
                                href: "https://api.kestra.io/v1/communities/slack/redirect",
                                title: "Slack",
                                icon: {
                                    element: shallowRef(Slack),
                                    class: "menu-icon"
                                },
                                external: true
                            },
                            {
                                href: "https://github.com/kestra-io/kestra/issues",
                                title: this.$t("documentation.github"),
                                icon: {
                                    element: shallowRef(Github),
                                    class: "menu-icon"
                                },
                                external: true
                            },

                        ]
                    },
                    {
                        href: "/settings",
                        alias: [
                            "/settings*"
                        ],
                        title: this.$t("settings"),
                        icon: {
                            element: shallowRef(CogOutline),
                            class: "menu-icon"
                        }
                    }
                ];
            }

        },
        created() {
            this.menu = this.disabledCurrentRoute(this.generateMenu());
        },
        watch: {
            $route() {
                this.menu = this.disabledCurrentRoute(this.generateMenu());
            }
        },
        data() {
            return {
                collapsed: localStorage.getItem("menuCollapsed") === "true",
                menu: []
            };
        },
        mounted() {
            this.$el.querySelectorAll(".vsm--item span").forEach(e => {
                //empty icon name on mouseover
                e.setAttribute("title","")
            })
        },
        computed: {
            ...mapState("misc", ["configs"])
        }
    };
</script>

<style lang="scss" scoped>
    @use 'element-plus/theme-chalk/src/mixins/function' as *;

    .logo {
        overflow: hidden;
        padding: 35px 0;
        height: 133px;
        position: relative;
        a {
            transition: 0.2s all;
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
        transition: 0.2s all;
        white-space: nowrap;
        font-size: getCssVar('font-size', 'extra-small');
        text-align: center;
        display: block;
        color: getCssVar('color', 'tertiary');
    }
</style>

<style lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/function' as *;

    #side-menu {
        z-index: 1039;
        border-right: 1px solid getCssVar('border-color');

        .vsm--list {
            transition: 0.2s all;
        }

        .vsm--icon {
            transition: left 0.2s ease;
            font-size: 1.5em;
            background-color: transparent !important;
            padding-bottom: 15px;
            height: 30px !important;
            width: 30px !important;
            svg {
                position: relative;
                margin-top: 13px;
            }
        }

        .vsm--item {
            transition: opacity 0.2s;

            * {
                transition: 0.2s all;
            }
        }

        .vsm--link {
            padding: 0.3rem 0.5rem;
            margin-bottom: 0.3rem;
            border-left: 4px solid transparent;
            padding-left: 37px;

            &_level-1 {
                &.vsm--link_exact-active,
                &.vsm--link_active {
                    box-shadow: none;
                    border-left: 4px solid getCssVar('color', 'secondary');
                }
            }

            &_exact-active,
            &_active {
                font-weight: 700;
            }
        }

        .vsm--toggle-btn {
            padding-top: 4px;
            background: transparent;
            color: getCssVar('color', 'secondary');
            height: 30px;
            border-top: 1px solid getCssVar('border-color');
        }

        &.vsm_collapsed .vsm--icon {
            left: 0;
        }


        a.vsm--link_active[href="#"] {
            cursor: initial !important;
        }


        .vsm--dropdown {
            .vsm--title {
                top: 3px;
            }
        }

        .vsm--dropdown_mobile-item {
            .vsm--item {
                .vsm--title {
                    left: 0;
                    position: relative;
                }
            }
        }

        a.vsm--link_active[href="#"] {
            cursor: initial !important;
        }

        html.dark & {
            background-color: getCssVar('gray-100-darken-5');

            .vsm--dropdown {
                background-color: getCssVar('gray-100-darken-5');
            }
        }

        &.vsm_collapsed {
            .logo {
                a {
                    left: 11px;
                }
            }

            .vsm--link {
                padding-left: 13px;
            }

            span.version {
                opacity: 0;
            }
        }
    }
</style>
