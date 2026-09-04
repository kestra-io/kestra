<template>
    <template v-if="ready">
        <FlowRootTopBar
            :routeInfo="routeInfo"
            :activeTabName="activeTabName"
        />
        <Tabs
            :routeName="routeName"
            :tabs="tabs"
        />
    </template>
    <div v-else class="full-space" v-ks-loading="true" />
</template>

<script setup lang="ts">
    import {useFlowRoot} from "./composables/useFlowRoot"
    import {useActiveTab} from "../../composables/useActiveTab"
    import useRouteContext from "../../composables/useRouteContext"
    import Tabs from "../../components/Tabs.vue"
    import FlowRootTopBar from "./FlowRootTopBar.vue"

    withDefaults(defineProps<{embed?: boolean}>(), {embed: false})

    const {tabs, routeName, routeInfo, ready, setupLifecycle} = useFlowRoot()
    const activeTabName = useActiveTab()

    useRouteContext(routeInfo)

    setupLifecycle()
</script>

<style scoped lang="scss">
    .full-space {
        flex: 1 1 auto;
    }
</style>
