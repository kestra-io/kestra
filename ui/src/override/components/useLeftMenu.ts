import {computed} from "vue";

import {useRoute, useRouter, type RouteRecordNameGeneric} from "vue-router";
import {useI18n} from "vue-i18n";

import {useMiscStore} from "override/stores/misc";

import {getDashboard} from "../../components/dashboard/composables/useDashboards";

import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue";
import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue";
import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
import PlayOutline from "vue-material-design-icons/PlayOutline.vue";
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue";
import FlaskOutline from "vue-material-design-icons/FlaskOutline.vue";
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
import PuzzleOutline from "vue-material-design-icons/PuzzleOutline.vue";
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue";
import OfficeBuildingOutline from "vue-material-design-icons/OfficeBuildingOutline.vue";
import ServerNetworkOutline from "vue-material-design-icons/ServerNetworkOutline.vue";
import Wrench from "vue-material-design-icons/Wrench.vue";

export type MenuItem = {
    title: string;
    routes?: RouteRecordNameGeneric[];
    href?: {
        name: string;
        params?: Record<string, any>;
        query?: Record<string, any>;
    };
    icon?: {
        element?: any;
        class?: any;
    };
    child?: MenuItem[];
    disabled?: boolean;
    attributes?: {
        locked?: boolean;
    };
    hidden?: boolean;
    exact?: boolean;
};

export function useLeftMenu() {
    const $route = useRoute();
    const $router = useRouter();

    const {t} = useI18n({useScope: "global"});

    const miscStore = useMiscStore();

    /**
     * Returns the names of all registered routes whose name starts with the given prefix.
     *
     * @param route - The route name prefix to match against.
     * @returns An array of route names starting with the provided prefix.
     */
    function routeStartWith(route: string) {
        return $router
            ?.getRoutes()
            .filter(
                (r) => typeof r.name === "string" && r.name.startsWith(route),
            )
            .map((r) => r.name);
    }

    /**
     * Flattens a hierarchical menu structure into a single-level array.
     *
     * Each menu item is included, followed by all of its children (recursively),
     * preserving the original order.
     *
     * @param items - An array of menu items that may contain nested children.
     * @returns A flat array containing all menu items and their descendants.
     */
    const flatMenuItems = (items: MenuItem[]): MenuItem[] => {
        return items.flatMap((item) =>
            item.child ? [item, ...flatMenuItems(item.child)] : [item],
        );
    };

    const menu = computed(() => {
        const generatedMenu = [
            {
                title: t("dashboards.labels.plural"),
                href: {
                    name: "home",
                    params: {
                        dashboard: getDashboard($route, "id"),
                    },
                },
                icon: {
                    element: ChartLineVariant,
                },
            },
            {
                title: t("flows"),
                routes: routeStartWith("flows"),
                href: {
                    name: "flows/list",
                },
                icon: {
                    element: FileTreeOutline,
                },
                exact: false,
            },
            {
                title: t("apps"),
                routes: routeStartWith("apps"),
                href: {
                    name: "apps/list",
                },
                icon: {
                    element: LayersTripleOutline,
                },
                attributes: {
                    locked: true,
                },
            },
            {
                title: t("templates"),
                routes: routeStartWith("templates"),
                href: {
                    name: "templates/list",
                },
                icon: {
                    element: ContentCopy, // TODO: Consider changing the icon for this
                },
                hidden: !miscStore.configs?.isTemplateEnabled,
            },
            {
                title: t("executions"),
                routes: routeStartWith("executions"),
                href: {
                    name: "executions/list",
                },
                icon: {
                    element: PlayOutline,
                },
            },
            {
                title: t("logs"),
                routes: routeStartWith("logs"),
                href: {
                    name: "logs/list",
                },
                icon: {
                    element: FileDocumentOutline,
                },
            },
            {
                title: t("demos.tests.label"),
                routes: routeStartWith("tests"),
                href: {
                    name: "tests/list",
                },
                icon: {
                    element: FlaskOutline,
                },
                attributes: {
                    locked: true,
                },
            },
            // TODO: To add Assets entry here in future release
            {
                title: t("namespaces"),
                routes: routeStartWith("namespaces"),
                href: {
                    name: "namespaces/list",
                },
                icon: {
                    element: FolderOpenOutline,
                },
            },
            {
                title: t("plugins.names"),
                routes: routeStartWith("plugins"),
                href: {
                    name: "plugins/list",
                },
                icon: {
                    element: PuzzleOutline,
                },
            },
            {
                title: t("blueprints.title"),
                routes: routeStartWith("blueprints"),
                icon: {
                    element: ShapePlusOutline,
                },
                child: [
                    {
                        title: t("blueprints.custom"),
                        routes: routeStartWith("blueprints/flow/custom"),
                        href: {
                            name: "blueprints",
                            params: {
                                kind: "flow",
                                tab: "custom",
                            },
                        },
                        icon: {
                            element: Wrench,
                        },
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        title: t("blueprints.flows"),
                        routes: routeStartWith("blueprints/flow/community"),
                        href: {
                            name: "blueprints",
                            params: {
                                kind: "flow",
                                tab: "community",
                            },
                        },
                        icon: {
                            element: FileTreeOutline,
                        },
                    },
                    {
                        title: t("blueprints.apps"),
                        routes: routeStartWith("blueprints/app"),
                        href: {
                            name: "blueprints",
                            params: {
                                kind: "app",
                                tab: "community",
                            },
                        },
                        icon: {
                            element: LayersTripleOutline,
                        },
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        title: t("blueprints.dashboards"),
                        routes: routeStartWith("blueprints/dashboard"),
                        href: {
                            name: "blueprints",
                            params: {
                                kind: "dashboard",
                                tab: "community",
                            },
                        },
                        icon: {
                            element: ChartLineVariant,
                        },
                    },
                ],
            },
            {
                title: t("tenant_administration"),
                // routes: routeStartWith("blueprints"), // TODO: change route
                icon: {
                    element: OfficeBuildingOutline,
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
                        // icon: {
                        //     element: DatabaseOutline,
                        //
                        // },
                    },
                    {
                        href: {name: "secrets/list"},
                        routes: routeStartWith("secrets"),
                        title: t("secret.names"),
                        icon: {
                            // element: ShieldKeyOutline,
                            //
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
                // routes: routeStartWith("admin"),
                icon: {
                    element: ServerNetworkOutline,
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
            if (menuItem.icon?.element) {
                menuItem.icon.class = "menu-icon";
            }

            if (menuItem.href && menuItem.href?.name === $route.name) {
                menuItem.href.query = {
                    ...$route.query,
                    ...menuItem.href?.query,
                };
            }
        });

        return generatedMenu;
    });

    return {menu};
}
