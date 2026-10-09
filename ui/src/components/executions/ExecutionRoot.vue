<template>
    <template v-if="ready">
        <ExecutionRootTopBar :routeInfo="routeInfo">
            <template #secondary v-if="secondaryAction">
                <component :is="secondaryAction" />
            </template>
        </ExecutionRootTopBar>
        <Tabs
            :routeName="routeName"
            :tabs="tabs"
        />
    </template>
    <div v-else class="full-space" v-ks-loading="true">
        {{ executionsStore.execution?.id }}
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useExecutionsStore} from "../../stores/executions"
    import {useExecutionRoot} from "./composables/useExecutionRoot"
    import {useActiveTab} from "../../composables/useActiveTab"
    import useRouteContext from "../../composables/useRouteContext"
    import Tabs from "../../components/Tabs.vue"
    import ExecutionRootTopBar from "./ExecutionRootTopBar.vue"
    import {secondaryActionComponents} from "override/components/executions/executionTabsExtension"

    const executionsStore = useExecutionsStore()

    const {routeInfo, routeName, ready, tabs, setupLifecycle} = useExecutionRoot()
    const activeTab = useActiveTab()
    const secondaryAction = computed(() => activeTab.value ? secondaryActionComponents[activeTab.value] : undefined)

    useRouteContext(routeInfo, false)

    setupLifecycle()
</script>
<style scoped lang="scss">
    .full-space {
        flex: 1 1 auto;
    }
</style>
