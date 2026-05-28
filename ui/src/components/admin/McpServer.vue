<template>
    <TopNavBar :title="details.title" :breadcrumb="details.breadcrumb" />
    <Tabs :tabs="tabs" :routeName="serverId ? 'admin/mcp-servers/update' : ''" />
</template>

<script lang="ts" setup>
    import {computed, watch, onMounted} from "vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import Tabs from "../Tabs.vue"
    import {useMcpStore} from "../../stores/mcp"
    import {useHelpers} from "./mcp/useHelpers"
    import {useMcpTabs} from "./mcp/useMcpTabs"
    import useRouteContext from "../../composables/useRouteContext"

    const mcpStore = useMcpStore()
    const {details, serverId} = useHelpers()
    const {tabs} = useMcpTabs()

    const context = computed(() => ({title: details.value.title}))
    useRouteContext(context)

    watch(serverId, (name) => {
        if (name) {
            mcpStore.load(name)
        } else {
            mcpStore.server = null
        }
    })

    onMounted(() => {
        const main = document.querySelector("main")
        if (main) main.scrollTop = 0

        if (serverId.value) {
            mcpStore.load(serverId.value)
        } else {
            mcpStore.server = null
        }
    })
</script>
