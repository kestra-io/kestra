import {Component, computed, Ref} from "vue";
import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";

import BlueprintsBrowser from "../../../override/components/flows/blueprints/BlueprintsBrowser.vue";
import Flows from "../../../components/flows/Flows.vue";
import Executions from "../../../components/executions/Executions.vue";
import Dependencies from "../../../components/dependencies/Dependencies.vue";
import NamespaceFilesEditorView from "../../../components/namespaces/components/NamespaceFilesEditorView.vue";
import NamespaceOverview from "../../../components/namespaces/components/NamespaceOverview.vue";

export interface Tab {
    locked?: boolean;
    disabled?: boolean;
    maximized?: boolean;
    name: string;
    title: string;
    component: Component;

    props?: Record<string, any>;
}

export interface Breadcrumb {
    label: string;
    link?: {
        name?: string,
        params?: {
            id: string,
            tab: string,
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
    "variables",
    "plugin-defaults",
    "kv",
    "files",
    "history",
    "audit-logs",
];

export function useHelpers() {
    const route = useRoute();
    const {t} = useI18n({useScope: "global"});

    const namespace = computed(() => route.params?.id) as Ref<string>;

    const parts = computed(() => namespace.value?.split(".") ?? []);
    const details: Ref<Details> = computed(() => ({
        title: parts.value.at(-1) || t("namespaces"),
        breadcrumb: [
            {label: t("namespaces"), link: {name: "namespaces/list"}},
            ...parts.value.map((_: string, index: number): Breadcrumb => ({
                label: parts.value[index],
                link: {
                    name: "namespaces/update",
                    params: {
                        id: parts.value.slice(0, index + 1).join("."),
                        tab: "overview",
                    },
                },
                disabled: index === parts.value.length - 1,
            })),
        ],
    }));

    const tabs: Tab[] = [
        // If it's a system namespace, include the blueprints tab
        ...(namespace.value === "system" ? [
            {
                name: "blueprints",
                title: t("blueprints.title"),
                component: BlueprintsBrowser,
                props: {tab: "community", system: true},
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
            props: {namespace: namespace.value, topbar: false},
        },
        {
            name: "executions",
            title: t("executions"),
            component: Executions,
            props: {
                namespace: namespace.value,
                topbar: false,
                visibleCharts: true,
                embed: false,
            },
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
    ];

    return {details, tabs};
}
