import {Component, computed, Ref} from "vue";
import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";

import BlueprintsBrowser from "../../../override/components/flows/blueprints/BlueprintsBrowser.vue";
import Dashboard from "../../../components/dashboard/Dashboard.vue";
import Flows from "../../../components/flows/Flows.vue";
import Executions from "../../../components/executions/Executions.vue";
import Dependencies from "../../../components/dependencies/Dependencies.vue";
import EditorView from "../../../components/inputs/EditorView.vue";

export interface Tab {
    locked?: boolean;
    maximized?: boolean;

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

    const namespace = computed(() => route.params?.id) as Ref<string>;

    const parts = computed(() => namespace.value?.split(".") ?? []);
    const details: Ref<Details> = computed(() => ({
        title: parts.value.at(-1) || t("namespaces"),
        breadcrumb: [
            {label: t("namespaces"), link: {name: "namespaces/list"}},
            ...parts.value.map((_: string, index: number) => ({
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
        ...(namespace.value === "system"
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
            },
        },
        {
            name: "dependencies",
            title: t("dependencies"),
            component: Dependencies,
            maximized: true,
        },
        {
            maximized: true,
            name: "files",
            title: t("files"),
            component: EditorView,
            props: {
                namespace: namespace.value,
                isNamespace: true,
                isReadOnly: false,
            },
        },
    ];

    return {details, tabs};
}
