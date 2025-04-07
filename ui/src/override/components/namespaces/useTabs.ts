import {useRoute} from "vue-router";
import {useI18n} from "vue-i18n";

import {Tab, ORDER} from "../../../components/namespaces/utils/useHelpers";

import DemoNamespace from "../../../components/demo/Namespace.vue";

import BlueprintsBrowser from "../flows/blueprints/BlueprintsBrowser.vue";

import Dashboard from "../../../components/dashboard/Dashboard.vue";
import Flows from "../../../components/flows/Flows.vue";
import Executions from "../../../components/executions/Executions.vue";
import Dependencies from "../../../components/namespaces/components/content/Dependencies.vue";
import KVTable from "../../../components/kv/KVTable.vue";
import EditorView from "../../../components/inputs/EditorView.vue";

const lockedProps = (tab: string) => ({
    locked: true,
    component: DemoNamespace,
    props: {tab, maximize: true},
});

export function useTabs() {
    const route = useRoute();
    const {t} = useI18n({useScope: "global"});

    const namespace = route.params?.id as string;

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
            props: {containerClass: "full-container flex-0"},
        },
        {
            ...lockedProps("edit"),
            name: "edit",
            title: t("edit"),
        },
        {
            name: "flows",
            title: t("flows"),
            component: Flows,
            props: {topbar: false},
        },
        {
            name: "executions",
            title: t("executions"),
            component: Executions,
            props: {topbar: false, visibleCharts: true},
        },
        {
            name: "dependencies",
            title: t("dependencies"),
            component: Dependencies,
            props: {type: "dependencies", namespace},
        },
        {
            ...lockedProps("secrets"),
            name: "secrets",
            title: t("secret.names"),
        },
        {
            ...lockedProps("variables"),
            name: "variables",
            title: t("variables"),
        },
        {
            ...lockedProps("plugin-defaults"),
            name: "plugin-defaults",
            title: t("plugin defaults"),
        },
        {
            name: "kv",
            title: t("kv.name"),
            component: KVTable,
            props: {namespace},
        },
        {
            name: "files",
            title: t("files"),
            component: EditorView,
            props: {isNamespace: true, isReadOnly: false},
        },
        {
            ...lockedProps("history"),
            name: "history",
            title: t("revisions"),
        },
        {
            ...lockedProps("audit-logs"),
            name: "audit-logs",
            title: t("auditlogs"),
        },
    ];

    // Ensure the order of tabs is following the ORDER array
    tabs.sort((a, b) => ORDER.indexOf(a.name) - ORDER.indexOf(b.name));

    return {tabs};
}
