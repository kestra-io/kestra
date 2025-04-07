import {Component} from "vue";
import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";

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

    const parts = namespace.split(".") ?? [];
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

    return {details};
}
