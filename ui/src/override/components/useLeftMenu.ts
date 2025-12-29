import {computed} from "vue";

import {useRoute, useRouter, type RouteRecordNameGeneric} from "vue-router";
import {useI18n} from "vue-i18n";

import {useMiscStore} from "override/stores/misc";

import {getDashboard} from "../../components/dashboard/composables/useDashboards";

// Main icons
import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue";
import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue";
import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
import PlayOutline from "vue-material-design-icons/PlayOutline.vue";
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue";
import FlaskOutline from "vue-material-design-icons/FlaskOutline.vue";
// import PackageVariantClosed from "vue-material-design-icons/PackageVariantClosed.vue";
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
import PuzzleOutline from "vue-material-design-icons/PuzzleOutline.vue";
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue";
import OfficeBuildingOutline from "vue-material-design-icons/OfficeBuildingOutline.vue";
import ServerNetworkOutline from "vue-material-design-icons/ServerNetworkOutline.vue";

// Blueprints icons
import Wrench from "vue-material-design-icons/Wrench.vue";

// Tenant Administration icons
import Monitor from "vue-material-design-icons/Monitor.vue";
import DatabaseOutline from "vue-material-design-icons/DatabaseOutline.vue";
import LockOutline from "vue-material-design-icons/LockOutline.vue";
import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
import Battery40 from "vue-material-design-icons/Battery40.vue";
import ShieldAccount from "vue-material-design-icons/ShieldAccount.vue";

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
    attributes?: {
        locked?: boolean;
    };
    hidden?: boolean;
};

export function useLeftMenu() {
    const $route = useRoute();
    const $router = useRouter();

    const {t} = useI18n({useScope: "global"});

    const configs = useMiscStore().configs;

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

    const menu = computed<MenuItem[]>(() => {
        return [
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
            // Uncomment PackageVariantClosed on line 25 and use as the icon
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
                title: t("templates"),
                routes: routeStartWith("templates"),
                href: {
                    name: "templates/list",
                },
                icon: {
                    element: ContentCopy,
                },
                hidden: !configs?.isTemplateEnabled,
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
                title: t("tenant.name"),
                routes: [
                    "admin/stats",
                    "kv",
                    "secrets",
                    "admin/triggers",
                    "admin/auditlogs",
                    "admin/iam",
                    "admin/concurrency-limits",
                ]
                    .map(routeStartWith)
                    .find((routes) => routes.length > 0),
                icon: {
                    element: OfficeBuildingOutline,
                },
                child: [
                    {
                        title: t("system overview"),
                        routes: routeStartWith("admin/stats"),
                        href: {
                            name: "admin/stats",
                        },
                        icon: {
                            element: Monitor,
                        },
                    },
                    {
                        title: t("kv.name"),
                        routes: routeStartWith("kv"),
                        href: {
                            name: "kv/list",
                        },
                        icon: {
                            element: DatabaseOutline,
                        },
                    },
                    {
                        title: t("secret.names"),
                        routes: routeStartWith("secrets"),
                        href: {
                            name: "secrets/list",
                        },
                        icon: {
                            element: LockOutline,
                        },
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        title: t("triggers"),
                        routes: routeStartWith("admin/triggers"),
                        href: {
                            name: "admin/triggers",
                        },
                        icon: {
                            element: LightningBolt,
                        },
                    },
                    {
                        title: t("auditlogs"),
                        routes: routeStartWith("admin/auditlogs"),
                        href: {
                            name: "admin/auditlogs/list",
                        },
                        icon: {
                            element: FileDocumentOutline,
                        },
                        attributes: {
                            locked: true,
                        },
                    },
                    {
                        title: t("concurrency limits"),
                        routes: routeStartWith("admin/concurrency-limits"),
                        href: {
                            name: "admin/concurrency-limits",
                        },
                        icon: {
                            element: Battery40,
                        },
                        hidden: !configs?.isConcurrencyViewEnabled,
                    },
                    {
                        title: t("iam"),
                        routes: routeStartWith("admin/iam"),
                        href: {
                            name: "admin/iam",
                        },
                        icon: {
                            element: ShieldAccount,
                        },
                        attributes: {
                            locked: true,
                        },
                    },
                ],
            },
            {
                title: t("instance"),
                routes: routeStartWith("admin/instance"),
                href: {
                    name: "admin/instance",
                },
                icon: {
                    element: ServerNetworkOutline,
                },
                attributes: {
                    locked: true,
                },
            },
        ].map((item: MenuItem) => {
            if (item.icon?.element) {
                item.icon.class = "menu-icon"; // Add default class to all menu icons
            }

            if (item.href && item.href?.name === $route.name) {
                item.href.query = {
                    ...$route.query,
                    ...item.href?.query,
                };
            }

            return item;
        });
    });

    return {menu};
}
