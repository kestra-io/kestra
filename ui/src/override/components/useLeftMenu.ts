import {computed} from "vue";
import {useRoute, useRouter} from "vue-router";
import {useI18n} from "vue-i18n";
import {useMiscStore} from "override/stores/misc";

import {getDashboard} from "../../components/dashboard/composables/useDashboards";

import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue";
import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue";
import PlayOutline from "vue-material-design-icons/PlayOutline.vue";
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue";
import FlaskOutline from "vue-material-design-icons/FlaskOutline.vue";
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
import PuzzleOutline from "vue-material-design-icons/PuzzleOutline.vue";
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue";
import OfficeBuildingOutline from "vue-material-design-icons/OfficeBuildingOutline.vue";
import ServerNetworkOutline from "vue-material-design-icons/ServerNetworkOutline.vue";

// TO REMOVE AFTER TESTING
import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
import DatabaseOutline from "vue-material-design-icons/DatabaseOutline.vue";
import ShieldKeyOutline from "vue-material-design-icons/ShieldKeyOutline.vue";

export type MenuItem = {
    href?: {
        path?: string;
        name: string;
        params?: Record<string, any>;
        query?: Record<string, any>;
    };
    child?: MenuItem[];
    disabled?: boolean;
};

export function useLeftMenu() {
    const {t} = useI18n({useScope: "global"});
    const $route = useRoute();
    const $router = useRouter();
    const miscStore = useMiscStore();

    /**
     * Returns all route names that start with the given route
     * @param route
     * @returns
     */
    function routeStartWith(route: string) {
        return $router
            ?.getRoutes()
            .filter(
                (r) => typeof r.name === "string" && r.name.startsWith(route),
            )
            .map((r) => r.name);
    }

    const flatMenuItems = (items: MenuItem[]): MenuItem[] => {
        return items.flatMap((item) =>
            item.child ? [item, ...flatMenuItems(item.child)] : [item],
        );
    };

    const menu = computed(() => {
        const generatedMenu = [
            {
                href: {
                    name: "home",
                    params: {dashboard: getDashboard($route, "id")},
                },
                title: t("dashboards.labels.plural"),
                icon: {
                    element: ChartLineVariant,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "flows/list"},
                routes: routeStartWith("flows"),
                title: t("flows"),
                icon: {
                    element: FileTreeOutline,
                    class: "menu-icon",
                },
                exact: false,
            },
            {
                href: {name: "apps/list"},
                routes: routeStartWith("apps"),
                title: t("apps"),
                icon: {
                    element: LayersTripleOutline,
                    class: "menu-icon",
                },
                attributes: {
                    locked: true,
                },
            },
            {
                href: {name: "templates/list"},
                routes: routeStartWith("templates"),
                title: t("templates"),
                icon: {
                    element: ContentCopy, // TODO: maybe change icon
                    class: "menu-icon",
                },
                hidden: !miscStore.configs?.isTemplateEnabled,
            },
            {
                href: {name: "executions/list"},
                routes: routeStartWith("executions"),
                title: t("executions"),
                icon: {
                    element: PlayOutline,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "logs/list"},
                routes: routeStartWith("logs"),
                title: t("logs"),
                icon: {
                    element: FileDocumentOutline,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "tests/list"},
                routes: routeStartWith("tests"),
                title: t("demos.tests.label"),
                icon: {
                    element: FlaskOutline,
                    class: "menu-icon",
                },
                attributes: {
                    locked: true,
                },
            },
            {
                href: {name: "namespaces/list"},
                routes: routeStartWith("namespaces"),
                title: t("namespaces"),
                icon: {
                    element: FolderOpenOutline,
                    class: "menu-icon",
                },
            },
            {
                href: {name: "plugins/list"},
                routes: routeStartWith("plugins"),
                title: t("plugins.names"),
                icon: {
                    element: PuzzleOutline,
                    class: "menu-icon",
                },
            },
            {
                routes: routeStartWith("blueprints"),
                title: t("blueprints.title"),
                icon: {
                    element: ShapePlusOutline,
                    class: "menu-icon",
                },
                child: [
                    {
                        title: t("blueprints.custom"),
                        routes: routeStartWith("blueprints/flow/custom"),
                        attributes: {
                            locked: true,
                        },
                        href: {
                            name: "blueprints",
                            params: {kind: "flow", tab: "custom"},
                        },
                    },
                    {
                        title: t("blueprints.flows"),
                        routes: routeStartWith("blueprints/flow"),
                        href: {
                            name: "blueprints",
                            params: {kind: "flow", tab: "community"},
                        },
                    },
                    {
                        title: t("blueprints.apps"),
                        routes: routeStartWith("blueprints/flow/app"),
                        attributes: {
                            locked: true,
                        },
                        href: {
                            // TODO: napravi da ide na demo page
                            name: "blueprints",
                            params: {kind: "flow", tab: "app"},
                        },
                    },
                    {
                        title: t("blueprints.dashboards"),
                        routes: routeStartWith("blueprints/dashboard"),
                        href: {
                            name: "blueprints",
                            params: {kind: "dashboard", tab: "community"},
                        },
                    },
                ],
            },
            {
                routes: routeStartWith("blueprints"), // TODO: change route
                title: t("tenant_administration"),
                icon: {
                    element: OfficeBuildingOutline,
                    class: "menu-icon",
                },
                child: [
                    {
                        href: {name: "admin/stats"},
                        routes: routeStartWith("admin/stats"),
                        title: t("system overview"),
                    },
                    {
                        href: {name: "kv/list"},
                        routes: routeStartWith("kv"),
                        title: t("kv.name"),
                        icon: {
                            element: DatabaseOutline,
                            class: "menu-icon",
                        },
                    },
                    {
                        href: {name: "secrets/list"},
                        routes: routeStartWith("secrets"),
                        title: t("secret.names"),
                        icon: {
                            element: ShieldKeyOutline,
                            class: "menu-icon",
                        },
                        attributes: {
                            locked: true,
                        },
                    },

                    {
                        href: {name: "admin/triggers"},
                        routes: routeStartWith("admin/triggers"),
                        title: t("triggers"),
                    },
                    {
                        href: {name: "admin/auditlogs/list"},
                        routes: routeStartWith("admin/auditlogs"),
                        title: t("auditlogs"),
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        href: {name: "admin/iam"},
                        routes: routeStartWith("admin/iam"),
                        title: t("iam"),
                        attributes: {
                            locked: true,
                        },
                    },
                ],
            },
            {
                title: t("instance_administration"),
                routes: routeStartWith("admin"),
                icon: {
                    element: ServerNetworkOutline,
                    class: "menu-icon",
                },
                child: [
                    {
                        href: {name: "admin/instance"},
                        routes: routeStartWith("admin/instance"),
                        title: t("instance"),
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        href: {name: "admin/tenants/list"},
                        routes: routeStartWith("admin/tenants"),
                        title: t("tenant.names"),
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        href: {name: "admin/concurrency-limits"},
                        routes: routeStartWith("admin/concurrency-limits"),
                        title: t("concurrency limits"),
                        hidden: !miscStore.configs?.isConcurrencyViewEnabled,
                    },
                ],
            },
        ];

        flatMenuItems(generatedMenu).forEach((menuItem) => {
            if (
                menuItem.href !== undefined &&
                menuItem.href?.name === $route.name
            ) {
                menuItem.href.query = {
                    ...$route.query,
                    ...menuItem.href?.query,
                };
            }
        });

        return generatedMenu;
    });

    return {
        routeStartWith,
        menu,
    };
}
