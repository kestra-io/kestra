<template>
    <side-bar :generate-menu="generateMenu" />
</template>

<script setup>
    import {shallowRef, computed} from "vue";
    import {useStore} from "vuex";
    import {useRouter} from "vue-router";
    import {useI18n} from "vue-i18n";

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
    import ChartBoxOutline from "vue-material-design-icons/ChartBoxOutline.vue";
    import Connection from "vue-material-design-icons/Connection.vue";
    import VectorIntersection from "vue-material-design-icons/VectorIntersection.vue";
    import AccountOutline from "vue-material-design-icons/AccountOutline.vue";
    import ShieldCheckOutline from "vue-material-design-icons/ShieldCheckOutline.vue";
    import ServerOutline from "vue-material-design-icons/ServerOutline.vue";
    import ShieldLockOutline from "vue-material-design-icons/ShieldLockOutline.vue"
    import FileTableOutline from "vue-material-design-icons/FileTableOutline.vue";

    import SideBar from "../../components/layout/SideBar.vue"

    const {t} = useI18n()
    const $router = useRouter()
    const store = useStore()

    function routeStartWith(route) {
        return $router?.getRoutes().filter(r => r.name.startsWith(route)).map(r => r.name);
    }

    const configs = computed(() => store.state.misc.configs);

    // This object seems to be a good candidate for a computed value
    // but cannot be. When it becomes a computed, the hack to set current
    // route as active in the blueprints activates pages forever.
    const generateMenu = () => {
        return [
            {
                href: {name: "home"},
                title: t("homeDashboard.title"),
                icon: {
                    element: shallowRef(ViewDashboardVariantOutline),
                    class: "menu-icon",
                },
            },
            {
                href: {name: "flows/list"},
                routes: routeStartWith("flows"),
                title: t("flows"),
                icon: {
                    element: shallowRef(FileTreeOutline),
                    class: "menu-icon",
                },
                exact: false,
            },
            {
                href: {name: "templates/list"},
                routes: routeStartWith("templates"),
                title: t("templates"),
                icon: {
                    element: shallowRef(ContentCopy),
                    class: "menu-icon",
                },
                hidden: !configs.value.isTemplateEnabled
            },
            {
                href: {name: "executions/list"},
                routes: routeStartWith("executions"),
                title: t("executions"),
                icon: {
                    element: shallowRef(TimelineClockOutline),
                    class: "menu-icon"
                },
            },
            {
                href: {name: "taskruns/list"},
                routes: routeStartWith("taskruns"),
                title: t("taskruns"),
                icon: {
                    element: shallowRef(ChartTimeline),
                    class: "menu-icon"
                },
                hidden: !configs.value.isTaskRunEnabled
            },
            {
                href: {name: "logs/list"},
                routes: routeStartWith("logs"),
                title: t("logs"),
                icon: {
                    element: shallowRef(TimelineTextOutline),
                    class: "menu-icon"
                },
            },
            {
                href: {name: "namespaces"},
                routes: routeStartWith("namespaces"),
                title: t("namespaces"),
                icon: {
                    element: shallowRef(VectorIntersection),
                    class: "menu-icon"
                }
            },
            {
                href: {name: "blueprints"},
                routes: routeStartWith("blueprints"),
                title: t("blueprints.title"),
                icon: {
                    element: shallowRef(BallotOutline),
                    class: "menu-icon"
                },
            },
            {
                href: {name: "plugins/list"},
                routes: routeStartWith("plugins"),
                title: t("plugins.names"),
                icon: {
                    element: shallowRef(Connection),
                    class: "menu-icon"
                },
            },
            {
                href: {name: "docs/view"},
                routes: routeStartWith("docs/view"),
                title: t("docs"),
                icon: {
                    element: shallowRef(FileTableOutline),
                    class: "menu-icon"
                }
            },
            {
                title: t("administration"),
                routes: routeStartWith("admin"),
                icon: {
                    element: shallowRef(ShieldAccountVariantOutline),
                    class: "menu-icon"
                },
                child: [
                    {
                        title: t("iam"),
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
                        title: t("auditlogs"),
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
                        routes: routeStartWith("admin/triggers"),
                        title: t("triggers"),
                        icon: {
                            element: shallowRef(TimerCogOutline),
                            class: "menu-icon"
                        }
                    },
                    {
                        title: t("cluster"),
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
                        title: t("tenants"),
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
                        routes: routeStartWith("admin/stats"),
                        title: t("stats"),
                        icon: {
                            element: shallowRef(ChartBoxOutline),
                            class: "menu-icon"
                        },
                    }
                ]
            },
            {
                href: {name: "settings"},
                routes: routeStartWith("admin/settings"),
                title: t("settings.label"),
                icon: {
                    element: shallowRef(CogOutline),
                    class: "menu-icon"
                }
            }
        ];
    }
</script>
