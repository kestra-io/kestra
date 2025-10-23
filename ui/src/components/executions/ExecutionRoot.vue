<template>
    <template v-if="ready">
        <ExecutionRootTopBar :routeInfo="routeInfo" />
        <Tabs
            :routeName="routeName"
            @follow="follow"
            :tabs="tabs"
        />
    </template>
    <div v-else class="full-space" v-loading="true">
        {{ executionsStore.execution?.id }}
    </div>
</template>

<script setup lang="ts">
    import {useExecutionsStore} from "../../stores/executions";
    import {useExecutionRoot} from "./composables/useExecutionRoot";
    import useRouteContext from "../../composables/useRouteContext";
    //@ts-expect-error no declaration file
    import Tabs from "../../components/Tabs.vue";
    //@ts-expect-error no declaration file
    import ExecutionRootTopBar from "./ExecutionRootTopBar.vue";

    const executionsStore = useExecutionsStore();

    const {routeInfo, routeName, ready, follow, tabs, setupLifecycle} = useExecutionRoot();

    useRouteContext(routeInfo as any, false);

    setupLifecycle();
</script>
<style scoped lang="scss">
    .full-space {
        flex: 1 1 auto;
    }
</style>
