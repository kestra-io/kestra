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
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import TimelineClockOutline from "vue-material-design-icons/TimelineClockOutline.vue";
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
    import NotebookOutline from "vue-material-design-icons/NotebookOutline.vue";
    import BookMultipleOutline from "vue-material-design-icons/BookMultipleOutline.vue";
    import FileCodeOutline from "vue-material-design-icons/FileCodeOutline.vue";
    import GoogleCirclesExtended from "vue-material-design-icons/GoogleCirclesExtended.vue";
    import Slack from "vue-material-design-icons/Slack.vue";
    import Github from "vue-material-design-icons/Github.vue";
    import CogOutline from "vue-material-design-icons/CogOutline.vue";
    import ViewDashboardVariantOutline from "vue-material-design-icons/ViewDashboardVariantOutline.vue";
    import FileDocumentArrowRightOutline from "vue-material-design-icons/FileDocumentArrowRightOutline.vue";
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

                        if (r.href !== "/" && this.$route.path.startsWith(r.href)) {
                            r.class = "vsm--link_active"
                        }

                        return r;
                    })
            },
            generateMenu() {
                return [
                    {
                        href: "/",
                        title: this.$t("home"),
                        icon: {
                            element: shallowRef(ViewDashboardVariantOutline),
                            class: "menu-icon",
                        },
                    },
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
                                href: "https://kestra.io/docs/flow-examples/",
                                title: this.$t("documentation.examples"),
                                icon: {
                                    element: shallowRef(FileDocumentArrowRightOutline),
                                    class: "menu-icon"
                                },
                                external: true
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

<style lang="scss">
    #side-menu {
        z-index: 1039;
        border-right: 1px solid var(--bs-border-color);

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
                    transition: 0.2s all;

                    html.dark & {
                        background: url(../../../src/assets/logo-white.svg) 0 0 no-repeat;
                        background-size: 179px 55px;
                    }
                }
            }
        }

        span.version {
            transition: 0.2s all;
            white-space: nowrap;
            font-size: var(--el-font-size-extra-small);
            text-align: center;
            display: block;
            color: var(--bs-gray-400);

            html.dark & {
                color: var(--bs-gray-600);
            }
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
            padding: 0 30px;
            transition: padding 0.2s ease;
        }

        .vsm--child {
            .vsm--item {
                padding: 0;
            }
        }
        .vsm--link {
            padding: 0.3rem 0.5rem;
            margin-bottom: 0.3rem;
            border-radius: var(--bs-border-radius-lg);
            transition: padding 0.2s ease;

            html.dark & {
                color: var(--bs-white);
            }

            &_exact-active,
            &_active {
                font-weight: 700;
                background-color: var(--bs-white);
                color: var(--bs-primary);
                box-shadow: 0 0.5rem 0.5rem var(--bs-gray-300);

                html.dark & {
                    background-color: var(--bs-primary);
                    box-shadow: none;
                }
            }

            &_disabled {
                opacity: 1;
            }
        }

        .vsm--toggle-btn {
            padding-top: 4px;
            background: transparent;
            color: var(--bs-secondary);
            height: 30px;
            border-top: 1px solid var(--bs-border-color);
        }


        a.vsm--link_active[href="#"] {
            cursor: initial !important;
        }

        .vsm--dropdown {
            background-color: var(--bs-gray-100);
            .vsm--title {
                top: 3px;
            }
        }


        a.vsm--link_active[href="#"] {
            cursor: initial !important;
        }

        html.dark & {
            background-color: var(--bs-gray-100);

            .vsm--dropdown {
                background-color: var(--bs-gray-100);
            }
        }


        .vsm--mobile-bg {
            border-radius: 0 var(--bs-border-radius) var(--bs-border-radius) 0 ;
        }

        &.vsm_collapsed {
            .logo {
                a {
                    left: 8px;

                    span.img {
                        background-size: 207px 55px !important;
                    }
                }
            }

            .vsm--link {
                padding-left: 13px;
            }

            .vsm--item {
                padding: 0 5px;
            }

            span.version {
                opacity: 0;
            }
        }
    }
</style>
