import {computed, onMounted, ref} from "vue"

import {useRoute, useRouter} from "vue-router"
import type {
    RouteLocationRaw,
    RouteLocationNamedRaw,
    RouteRecordNameGeneric,
} from "vue-router"

import {useI18n} from "vue-i18n"

import {useMiscStore} from "override/stores/misc"

import {shouldShowWelcome} from "../../utils/welcomeGuard"

// Main icons
import AiMenuIcon from "../../components/ai/AiMenuIcon.vue"
import ChartLineVariant from "vue-material-design-icons/ChartLineVariant.vue"
import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
import LayersTripleOutline from "vue-material-design-icons/LayersTripleOutline.vue"
import PlayOutline from "vue-material-design-icons/PlayOutline.vue"
import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
import FlaskOutline from "vue-material-design-icons/FlaskOutline.vue"
import PackageVariantClosed from "vue-material-design-icons/PackageVariantClosed.vue"
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
import PuzzleOutline from "vue-material-design-icons/PuzzleOutline.vue"
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue"

// Tenant Administration icons
import Monitor from "vue-material-design-icons/Monitor.vue"
import DatabaseOutline from "vue-material-design-icons/DatabaseOutline.vue"
import LockOutline from "vue-material-design-icons/LockOutline.vue"
import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
import Battery40 from "vue-material-design-icons/Battery40.vue"
import ShieldAccount from "vue-material-design-icons/ShieldAccount.vue"
import McpIcon from "../../components/McpIcon.vue"

export type MenuItem = {
    id?: string; // Generated at the end of menu computation
    title: string;
    header?: boolean;
    routes?: RouteRecordNameGeneric[];
    href?: RouteLocationRaw;
    icon?: {
        element?: any;
        class?: any;
    };
    child?: MenuItem[];
    attributes?: {
        locked?: boolean;
    };
    hidden?: boolean;
    disabled?: boolean;
    "class"?: string;
};

export function useLeftMenu() {
    const $route = useRoute()
    const $router = useRouter()

    const {t} = useI18n({useScope: "global"})

    const configs = useMiscStore().configs
    const showWelcomeLink = ref(false)

    const loadWelcomeLink = async () => {
        try {
            showWelcomeLink.value = await shouldShowWelcome()
        } catch {
            showWelcomeLink.value = false
        }
    }

    onMounted(() => {
        void loadWelcomeLink()
    })

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
            .map((r) => r.name)
    }

    /**
     * Recursively flattens a nested menu structure into a flat array.
     *
     * Each item is included in the result. If an item has `child` items,
     * they are recursively flattened and included immediately after the parent item.
     *
     * @param {MenuItem[]} items - The array of menu items to flatten. Each item may have a `child` property containing nested MenuItems.
     * @returns {MenuItem[]} A flat array of all menu items, preserving the parent-child order.
     */
    const flatten = (items: MenuItem[]): MenuItem[] => {
        return items.flatMap((item) =>
            item.child ? [item, ...flatten(item.child)] : [item],
        )
    }

    const menu = computed<MenuItem[]>(() => {
        const generated = [
            {
                title: "Workspace",
                child: [
                    {
                        title: t("dashboards.labels.plural"),
                        routes: routeStartWith("home"),
                        href: {
                            name: "home",
                        },
                        icon: {
                            element: ChartLineVariant,
                        },
                    },
                    {
                        title: t("ai.flow.title"),
                        routes: routeStartWith("welcome"),
                        href: {
                            name: "welcome",
                        },
                        icon: {
                            element: AiMenuIcon,
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
                ],
            },
            {
                title: "Resources",
                child: [
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
                        title: t("demos.assets.label"),
                        routes: routeStartWith("assets"),
                        href: {
                            name: "assets/list",
                        },
                        icon: {
                            element: PackageVariantClosed,
                        },
                        attributes: {
                            locked: true,
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
                        href: {
                            name: "blueprints",
                            params: {
                                kind: "flow",
                                tab: "community",
                            },
                        },
                        icon: {
                            element: ShapePlusOutline,
                        },
                    },
                ],
            },
            {
                title: t("tenant.name"),
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
                        title: t("mcp.servers"),
                        routes: routeStartWith("admin/mcp-servers"),
                        href: {
                            name: "admin/mcp-servers",
                        },
                        icon: {
                            element: McpIcon,
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
        ]

        flatten(generated).forEach((item: MenuItem) => {
            item.id = item.title.toLowerCase().replaceAll(" ", "-")

            if (item.icon?.element) item.icon.class = "menu-icon"

            if (item.href && typeof item.href !== "string") {
                const rObject = item.href as RouteLocationNamedRaw

                // Merge query if route matches
                if (rObject.name === $route.name) {
                    rObject.query = {
                        ...$route.query,
                        ...rObject.query,
                    }
                }

                // Convert object href to string path
                item.href = $router.resolve(rObject).fullPath
            }
        })

        return generated
    })

    return {menu}
}
