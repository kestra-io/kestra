import {Component} from "vue";
import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";

import BlueprintsBrowser from "../../../override/components/flows/blueprints/BlueprintsBrowser.vue";
import Dashboard from "../../../components/dashboard/Dashboard.vue";
import Flows from "../../../components/flows/Flows.vue";
import Executions from "../../../components/executions/Executions.vue";
import Dependencies from "../../../components/namespaces/components/content/Dependencies.vue";
import EditorView from "../../../components/inputs/EditorView.vue";

export interface Tab {
    locked?: boolean;

    name: string;
    title: string;
    component: Component;

    props?: Record<string, any>;
}

interface Details {
    title: string;
    breadcrumb: Record<string, any>[];
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

    const namespace = route.params?.id as string;

    const parts = namespace?.split(".") ?? [];
    const details: Details = {
        title: parts.at(-1) || t("namespaces"),
        breadcrumb: [
            {label: t("namespaces"), link: {name: "namespaces/list"}},
            ...parts.map((_: string, index: number) => ({
                label: parts[index],
                link: {
                    name: "namespaces/update",
                    params: {
                        id: parts.slice(0, index + 1).join("."),
                        tab: "overview",
                    },
                },
                disabled: index === parts.length - 1,
            })),
        ],
    };

    const tabs: Tab[] = [
        // If it's a system namespace, include the blueprints tab
        ...(namespace === "system"
            ? [
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
            component: Dashboard,
            props: {namespace, containerClass: "full-container flex-0"},
        },
        {
            name: "flows",
            title: t("flows"),
            component: Flows,
            props: {namespace, topbar: false},
        },
        {
            name: "executions",
            title: t("executions"),
            component: Executions,
            props: {namespace, topbar: false, visibleCharts: true},
        },
        {
            name: "dependencies",
            title: t("dependencies"),
            component: Dependencies,
            props: {namespace, type: "dependencies"},
        },
        {
            name: "files",
            title: t("files"),
            component: EditorView,
            props: {namespace, isNamespace: true, isReadOnly: false},
        },
    ];

    return {details, tabs};
}
