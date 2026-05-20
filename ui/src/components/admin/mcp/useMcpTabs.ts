import {computed} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"
import McpEdit from "./tabs/McpEdit.vue"
import McpConnect from "./tabs/McpConnect.vue"
import McpToolFlows from "./tabs/McpToolFlows.vue"

export const ORDER = ["edit", "connect", "tool-flows"]

export function useMcpTabs() {
    const route = useRoute()
    const {t} = useI18n({useScope: "global"})

    const isCreate = computed(() => route.name === "admin/mcp-servers/create")

    const tabs = computed(() => [
        {
            name: "edit",
            title: t("mcp.tab_edit"),
            component: McpEdit,
        },
        {
            name: "connect",
            title: t("mcp.tab_connect"),
            component: McpConnect,
            disabled: isCreate.value,
        },
        {
            name: "tool-flows",
            title: t("mcp.tab_tool_flows"),
            component: McpToolFlows,
            disabled: isCreate.value,
        },
    ])

    return {tabs}
}
