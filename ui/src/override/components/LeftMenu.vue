<template>
    <sidebar-menu
        data-component="FILENAME_PLACEHOLDER"
        id="side-menu"
        :menu="localMenu"
        @update:collapsed="onToggleCollapse"
        width="268px"
        :collapsed="collapsed"
        link-component-name="LeftMenuLink"
    >
        <template #header>
            <div class="logo">
                <router-link :to="{name: 'home'}">
                    <span class="img" />
                </router-link>
            </div>
            <Environment />
        </template>

        <template #footer />

        <template #toggle-icon>
            <el-button>
                <chevron-double-right v-if="collapsed" />
                <chevron-double-left v-else />
            </el-button>
            <span class="version">
                <el-tooltip
                    effect="light"
                    :persistent="false"
                    transition=""
                    :hide-after="0"
                >
                    <template #content>
                        <code>{{ configs.commitId }}</code> <DateAgo v-if="configs.commitDate" :inverted="true" :date="configs.commitDate" />
                    </template>
                    {{ configs.version }}
                </el-tooltip>
            </span>
        </template>
    </sidebar-menu>
</template>

<script>
    import {SidebarMenu} from "vue-sidebar-menu";
    import Environment from "../../components/layout/Environment.vue";
    import ChevronDoubleLeft from "vue-material-design-icons/ChevronDoubleLeft.vue";
    import ChevronDoubleRight from "vue-material-design-icons/ChevronDoubleRight.vue";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import TimelineClockOutline from "vue-material-design-icons/TimelineClockOutline.vue";
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
    import ChartTimeline from "vue-material-design-icons/ChartTimeline.vue";
    import BallotOutline from "vue-material-design-icons/BallotOutline.vue";
    import ShieldAccountVariantOutline from "vue-material-design-icons/ShieldAccountVariantOutline.vue";
    import CogOutline from "vue-material-design-icons/CogOutline.vue";
    import ViewDashboardVariantOutline from "vue-material-design-icons/ViewDashboardVariantOutline.vue";
    import TimerCogOutline from "vue-material-design-icons/TimerCogOutline.vue";
    import {mapState} from "vuex";
    import ChartBoxOutline from "vue-material-design-icons/ChartBoxOutline.vue";
    import Connection from "vue-material-design-icons/Connection.vue";
    import {shallowRef} from "vue";

    import VectorIntersection from "vue-material-design-icons/VectorIntersection.vue";
    import AccountOutline from "vue-material-design-icons/AccountOutline.vue";
    import ShieldCheckOutline from "vue-material-design-icons/ShieldCheckOutline.vue";
    import ServerOutline from "vue-material-design-icons/ServerOutline.vue";
    import ShieldLockOutline from "vue-material-design-icons/ShieldLockOutline.vue"
    import DateAgo from "../../components/layout/DateAgo.vue"
    import FileTableOutline from "vue-material-design-icons/FileTableOutline.vue";

    export default {
        components: {
            ChevronDoubleLeft,
            ChevronDoubleRight,
            SidebarMenu,
            Environment,
            DateAgo,
        },
        emits: ["menu-collapse"],
        methods: {
            flattenMenu(menu) {
                return menu.reduce((acc, item) => {
                    if (item.child) {
                        acc.push(...this.flattenMenu(item.child));
                    }

                    acc.push(item);
                    return acc;
                }, []);
            },
            onToggleCollapse(folded) {
                this.collapsed = folded;
                localStorage.setItem("menuCollapsed", folded ? "true" : "false");
                this.$emit("menu-collapse", folded);
            },
            disabledCurrentRoute(items) {
                return items
                    .map(r => {
                        if (r.href === this.$route.path) {
                            r.disabled = true;
                        }

                        // route hack is still needed for blueprints
                        if (r.href !== "/" && (this.$route.path.startsWith(r.href) || r.routes?.includes(this.$route.name))) {
                            r.class = "vsm--link_active";
                        }

                        if (r.child && r.child.some(c => this.$route.path.startsWith(c.href) || c.routes?.includes(this.$route.name))) {
                            r.class = "vsm--link_active";
                            r.child = this.disabledCurrentRoute(r.child);
                        }

                        return r;
                    })
            },
            routeStartWith(route) {
                return this.$router.getRoutes().filter(r => r.name.startsWith(route)).map(r => r.name);
            },
            generateMenu() {
                return [
                    {
                        href: {name: "home"},
                        title: this.$t("homeDashboard.title"),
                        icon: {
                            element: shallowRef(ViewDashboardVariantOutline),
                            class: "menu-icon",
                        },
                    },
                    {
                        href: {name: "flows/list"},
                        routes: this.routeStartWith("flows"),
                        title: this.$t("flows"),
                        icon: {
                            element: shallowRef(FileTreeOutline),
                            class: "menu-icon",
                        },
                        exact: false,
                    },
                    {
                        href: {name: "templates/list"},
                        routes: this.routeStartWith("templates"),
                        title: this.$t("templates"),
                        icon: {
                            element: shallowRef(ContentCopy),
                            class: "menu-icon",
                        },
                        hidden: !this.configs.isTemplateEnabled
                    },
                    {
                        href: {name: "executions/list"},
                        routes: this.routeStartWith("executions"),
                        title: this.$t("executions"),
                        icon: {
                            element: shallowRef(TimelineClockOutline),
                            class: "menu-icon"
                        },
                    },
                    {
                        href: {name: "taskruns/list"},
                        routes: this.routeStartWith("taskruns"),
                        title: this.$t("taskruns"),
                        icon: {
                            element: shallowRef(ChartTimeline),
                            class: "menu-icon"
                        },
                        hidden: !this.configs.isTaskRunEnabled
                    },
                    {
                        href: {name: "logs/list"},
                        routes: this.routeStartWith("logs"),
                        title: this.$t("logs"),
                        icon: {
                            element: shallowRef(TimelineTextOutline),
                            class: "menu-icon"
                        },
                    },
                    {
                        href: {name: "namespaces"},
                        routes: this.routeStartWith("namespaces"),
                        title: this.$t("namespaces"),
                        icon: {
                            element: shallowRef(VectorIntersection),
                            class: "menu-icon"
                        }
                    },
                    {
                        href: {name: "blueprints"},
                        routes: this.routeStartWith("blueprints"),
                        title: this.$t("blueprints.title"),
                        icon: {
                            element: shallowRef(BallotOutline),
                            class: "menu-icon"
                        },
                    },
                    {
                        href: {name: "plugins/list"},
                        routes: this.routeStartWith("plugins"),
                        title: this.$t("plugins.names"),
                        icon: {
                            element: shallowRef(Connection),
                            class: "menu-icon"
                        },
                    },
                    {
                        title: this.$t("administration"),
                        routes: this.routeStartWith("admin"),
                        icon: {
                            element: shallowRef(ShieldAccountVariantOutline),
                            class: "menu-icon"
                        },
                        child: [
                            {
                                title: this.$t("iam"),
                                icon: {
                                    element: shallowRef(AccountOutline),
                                    class: "menu-icon"
                                },
                                disabled: true,
                                attributes: {
                                    locked: true
                                }
                            },
                            {
                                title: this.$t("auditlogs"),
                                icon: {
                                    element: shallowRef(ShieldCheckOutline),
                                    class: "menu-icon"
                                },
                                disabled: true,
                                attributes: {
                                    locked: true
                                }
                            },
                            {
                                href: {name: "admin/triggers"},
                                routes: this.routeStartWith("admin/triggers"),
                                title: this.$t("triggers"),
                                icon: {
                                    element: shallowRef(TimerCogOutline),
                                    class: "menu-icon"
                                }
                            },
                            {
                                title: this.$t("cluster"),
                                icon: {
                                    element: shallowRef(ServerOutline),
                                    class: "menu-icon"
                                },
                                disabled: true,
                                attributes: {
                                    locked: true
                                }
                            },
                            {
                                title: this.$t("tenants"),
                                icon: {
                                    element: shallowRef(ShieldLockOutline),
                                    class: "menu-icon"
                                },
                                disabled: true,
                                attributes: {
                                    locked: true
                                }
                            },
                            {
                                href: {name: "admin/stats"},
                                routes: this.routeStartWith("admin/stats"),
                                title: this.$t("stats"),
                                icon: {
                                    element: shallowRef(ChartBoxOutline),
                                    class: "menu-icon"
                                },
                            }
                        ]
                    },
                    {
                        href: {name: "docs/view"},
                        routes: this.routeStartWith("docs/view"),
                        title: this.$t("docs"),
                        icon: {
                            element: shallowRef(FileTableOutline),
                            class: "menu-icon"
                        }
                    },
                    {
                        href: {name: "settings"},
                        routes: this.routeStartWith("admin/settings"),
                        title: this.$t("settings.label"),
                        icon: {
                            element: shallowRef(CogOutline),
                            class: "menu-icon"
                        }
                    }
                ];
            },
            expandParentIfNeeded() {
                document.querySelectorAll(".vsm--link.vsm--link_level-1.vsm--link_active:not(.vsm--link_open)[aria-haspopup]").forEach(e => {
                    e.click()
                });
            }
        },
        updated() {
            // Required here because in mounted() the menu is not yet rendered
            this.expandParentIfNeeded();
        },
        watch: {
            menu: {
                handler(newVal, oldVal) {
                    // Check if the active menu item has changed, if yes then update the menu
                    if (JSON.stringify(this.flattenMenu(newVal).map(e => e.class?.includes("vsm--link_active") ?? false)) !==
                        JSON.stringify(this.flattenMenu(oldVal).map(e => e.class?.includes("vsm--link_active") ?? false))) {
                        this.localMenu = newVal;
                        this.$el.querySelectorAll(".vsm--item span").forEach(e => {
                            //empty icon name on mouseover
                            e.setAttribute("title", "")
                        });
                    }
                },
                flush: "post",
                deep: true
            },
        },
        data() {
            return {
                collapsed: localStorage.getItem("menuCollapsed") === "true",
                localMenu: []
            };
        },
        computed: {
            ...
                mapState("misc", ["configs"]),
            menu() {
                return this.disabledCurrentRoute(this.generateMenu());
            },
        },
        mounted() {
            this.localMenu = this.menu;
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
            height: 113px;
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
            font-size: var(--font-size-xs);
            text-align: center;
            display: block;
            color: var(--bs-gray-600);
            width: auto;

            html.dark & {
                color: var(--bs-gray-800);
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

            &_disabled {
                pointer-events: auto;
            }

            .el-tooltip__trigger {
                display: flex;
            }
        }

        .vsm--toggle-btn {
            padding-top: 16px;
            padding-bottom: 16px;
            font-size: 20px;
            background: transparent;
            color: var(--bs-secondary);
            border-top: 1px solid var(--bs-border-color);

            .el-button {
                padding: 8px;
                margin-right: 15px;
                transition: margin-right 0.2s ease;
                html.dark & {
                    background: var(--bs-gray-500);
                }
            }
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
            border-radius: 0 var(--bs-border-radius) var(--bs-border-radius) 0;
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

            .el-button {
                margin-right: 0;
            }

            span.version {
                opacity: 0;
                width: 0;
            }
        }

        .el-tooltip__trigger .lock-icon.material-design-icon > .material-design-icon__svg {
            bottom: 0 !important;
            margin-left: 5px;
        }
    }

</style>
