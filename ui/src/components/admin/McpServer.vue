<template>
    <TopNavBar :title="details.title" :breadcrumb="details.breadcrumb">
        <template v-if="showCreateToolFlow" #actions>
            <KsButton type="primary" :icon="Plus" @click="createToolFlow">
                {{ $t("mcp.tools.create_tool_flow") }}
            </KsButton>
        </template>
    </TopNavBar>
    <Tabs :tabs="tabs" :routeName="serverId ? String(route.name) : ''" />
</template>

<script lang="ts" setup>
    import {computed, watch, onMounted} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import Plus from "vue-material-design-icons/Plus.vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import Tabs from "../Tabs.vue"
    import {useMcpStore} from "../../stores/mcp"
    import {useHelpers} from "./mcp/useHelpers"
    import {useMcpTabs} from "./mcp/useMcpTabs"
    import {useToolFlowCreation} from "./mcp/useToolFlowCreation"
    import useRouteContext from "../../composables/useRouteContext"

    const route = useRoute()
    const router = useRouter()
    const mcpStore = useMcpStore()
    const {details, serverId} = useHelpers()
    const {tabs} = useMcpTabs()
    const {canCreateFlow, createToolFlow} = useToolFlowCreation()

    const showCreateToolFlow = computed(() =>
        route.params.tab === "tool-flows" && canCreateFlow.value,
    )

    /**
     * The Connect and Tool Flows tabs depend on a persisted server configuration,
     * so they must stay unreachable until the server exists. Disabling the tab
     * buttons only guards clicks; this redirects direct URL access back to Edit.
     */
    const enforceLifecycle = () => {
        if (!serverId.value && route.params.tab && route.params.tab !== "edit") {
            router.replace({
                name: String(route.name),
                params: {...route.params, tab: "edit"},
            })
        }
    }

    const context = computed(() => ({title: details.value.title}))
    useRouteContext(context)

    watch(serverId, (name) => {
        if (name) {
            mcpStore.load(name)
        } else {
            mcpStore.server = null
        }
    })

    watch(() => route.params.tab, enforceLifecycle)

    onMounted(() => {
        const main = document.querySelector("main")
        if (main) main.scrollTop = 0

        enforceLifecycle()

        if (serverId.value) {
            mcpStore.load(serverId.value)
        } else {
            mcpStore.server = null
        }
    })
</script>
