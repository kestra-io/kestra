import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"

import {
    Tab,
    ORDER,
    useHelpers,
} from "../../../components/namespaces/utils/useHelpers"

import DemoNamespace from "../../../components/demo/Namespace.vue"

import KVTable from "../../../components/kv/KVTable.vue"

const lockedProps = (tab: string) => ({
    locked: true,
    component: DemoNamespace,
    props: {tab},
})

export function useTabs() {
    const route = useRoute()
    const {t} = useI18n({useScope: "global"})

    const namespace = route.params?.id as string

    const tabs: Tab[] = [
        ...useHelpers().tabs,
        {
            ...lockedProps("edit"),
            name: "edit",
            title: t("edit"),
        },
        {
            ...lockedProps("secrets"),
            name: "secrets",
            title: t("secret.names"),
        },
        {
            ...lockedProps("assets"),
            name: "assets",
            title: t("assets.title"),
        },
        {
            ...lockedProps("variables"),
            name: "variables",
            title: t("variables"),
        },
        {
            ...lockedProps("policies"),
            name: "policies",
            title: t("demos.policies.label"),
        },
        {
            name: "kv",
            title: t("kv.name"),
            component: KVTable,
            props: {namespace},
            fullContainer: true,
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
    ]

    // Ensure the order of tabs is following the ORDER array
    tabs.sort((a, b) => ORDER.indexOf(a.name) - ORDER.indexOf(b.name))

    return {tabs}
}
