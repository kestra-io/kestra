import {Component, computed, Ref} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"
import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "@kestra-io/design-system"
import {useNamespaceBreadcrumb} from "../../../composables/useNamespaceBreadcrumb"

import SystemBlueprintsTab from "../../flows/SystemBlueprintsTab.vue"
import Flows from "../../../components/flows/Flows.vue"
import Executions from "../../../components/executions/Executions.vue"
import Dependencies from "../../../components/dependencies/Dependencies.vue"
import NamespaceFilesEditorView from "../../../components/namespaces/components/NamespaceFilesEditorView.vue"
import NamespaceOverview from "../../../components/namespaces/components/NamespaceOverview.vue"
import {useSystemNamespace} from "../../../composables/useSystemNamespace"

export interface Tab {
    locked?: boolean;
    disabled?: boolean;
    maximized?: boolean;
    name: string;
    title: string;
    component: Component;
    props?: Record<string, unknown>;
    count?: number;
    fullContainer?: boolean;
}

interface Details {
    title: string;
    breadcrumb: KsBreadcrumbItem[];
    titleSiblings?: KsBreadcrumbLoader;
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

    const namespace = computed(() => route.params?.id) as Ref<string>
    const systemNamespace = useSystemNamespace()

    const levels = useNamespaceBreadcrumb(namespace)
    const details: Ref<Details> = computed(() => {
        const current = levels.value[levels.value.length - 1]
        return {
            title: current.label,
            breadcrumb: levels.value.slice(0, -1),
            titleSiblings: current.siblings,
        }
    })

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
