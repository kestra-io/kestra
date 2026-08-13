import {Component, computed, Ref} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"
import {NAMESPACE_PARENT_ROUTE} from "../../../utils/namespaceTabRoutes"

import SystemBlueprintsTab from "../../flows/SystemBlueprintsTab.vue"
import Flows from "../../../components/flows/Flows.vue"
import Executions from "../../../components/executions/Executions.vue"
import Dependencies from "../../../components/dependencies/Dependencies.vue"
import NamespaceFilesEditorView from "../../../components/namespaces/components/NamespaceFilesEditorView.vue"
import NamespaceOverview from "../../../components/namespaces/components/NamespaceOverview.vue"
import {useMiscStore} from "override/stores/misc"

export interface Tab {
    locked?: boolean;
    disabled?: boolean;
    maximized?: boolean;
    name: string;
    title: string;
    component: Component;
    props?: Record<string, any>;
    count?: number;
    blueprintDetail?: boolean;
    fullContainer?: boolean;
}

export interface Breadcrumb {
    label: string;
    link?: {
        name?: string,
        params?: {
            id: string,
        }
    },
    disabled?: boolean;
}

interface Details {
    title: string;
    breadcrumb: Breadcrumb[];
}

export const ORDER = [
    "blueprints",
    "overview",
    "edit",
    "flows",
    "executions",
    "dependencies",
    "secrets",
    "credentials",
    "assets",
    "variables",
    "policies",
    "kv",
    "reusable-inputs",
    "files",
    "history",
    "audit-logs",
]

export function useHelpers() {
    const route = useRoute()
    const {t} = useI18n({useScope: "global"})
    const miscStore = useMiscStore()

    const namespace = computed(() => route.params?.id) as Ref<string>
    const systemNamespace = computed(() => miscStore.configs?.systemNamespace ?? "system")

    const parts = computed(() => namespace.value?.split(".") ?? [])
    const details: Ref<Details> = computed(() => ({
        title: parts.value.at(-1) || t("namespaces"),
        breadcrumb: [
            {label: t("namespaces"), link: {name: "namespaces/list"}},
            ...parts.value.slice(0, -1).map((_: string, index: number): Breadcrumb => ({
                label: parts.value[index],
                link: {
                    name: `${NAMESPACE_PARENT_ROUTE}/overview`,
                    params: {
                        id: parts.value.slice(0, index + 1).join("."),
                    },
                },
            })),
        ],
    }))

    const tabs = computed<Tab[]>(() => [
        ...(namespace.value === systemNamespace.value ? [
            {
                name: "blueprints",
                title: t("recipe.section_title"),
                component: SystemBlueprintsTab,
                props: {namespace: namespace.value},
            },
        ]
            : []),
        {
            name: "overview",
            title: t("overview"),
            component: NamespaceOverview,
            props: {isNamespace: true, header: false},
        },
        {
            name: "flows",
            title: t("flows"),
            component: Flows,
            props: {
                namespace: namespace.value,
                topbar: false,
                fitHeight: true,
                defaultScopeFilter: false,
                embed: true,
            },
            fullContainer: true,
        },
        {
            name: "executions",
            title: t("executions"),
            component: Executions,
            props: {
                namespace: namespace.value,
                topbar: false,
                fitHeight: true,
                visibleCharts: true,
                embed: true,
                defaultScopeFilter: false,
            },
            fullContainer: true,
        },
        {
            name: "dependencies",
            title: t("dependencies"),
            component: Dependencies,
            maximized: true,
        },
        {
            name: "files",
            title: t("files"),
            component: NamespaceFilesEditorView,
            props: {namespace: namespace.value},
            maximized: true,
        },
    ])

    return {details, tabs}
}
