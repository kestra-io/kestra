<template>
    <div v-if="execution" class="wrapper">
        <KsCard class="banner" shadow="always">
            <Banner :execution @follow="emits('follow', $event)" />
        </KsCard>
        <div id="alerts">
            <ErrorAlert
                v-if="execution.state.current === State.FAILED"
                :execution
            />
        </div>
        <Topology
            class="topology"
            :horizontalDefault="!verticalLayout"
            @follow="emits('follow', $event)"
        />
        <PrevNext :execution />
    </div>
    <KsEmpty
        v-else
        id="empty"
        :description="$t('execution not found', {executionId: route.params.id})"
    />
</template>

<script setup lang="ts">
    import {onMounted, computed} from "vue"

    import {useRoute} from "vue-router"
    const route = useRoute()

    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")

    import {useExecutionsStore} from "../../../stores/executions"
    const store = useExecutionsStore()

    import {State} from "@kestra-io/design-system"

    import Banner from "./components/Banner.vue"
    import ErrorAlert from "./components/main/ErrorAlert.vue"
    import PrevNext from "./components/main/PrevNext.vue"
    import Topology from "../Topology.vue"

    const emits = defineEmits(["follow"])

    const execution = computed(() => store.execution)

    const loadExecution = (id: string) => store.loadExecution({id})

    onMounted(() => {
        if (!route.params.id) return
        loadExecution(route.params.id as string)
    })

    defineOptions({inheritAttrs: false})
</script>

<style scoped lang="scss">
    .wrapper {
        display: flex;
        flex-direction: column;
        height: 100%;
        gap: var(--ks-spacing-4);
    }

    .banner {
        flex-shrink: 0;
        width: 100%;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);

        :deep(.kel-card__body) {
            height: 100%;
            padding: 0;
        }
    }

    #alerts:empty {
        display: none;
    }

    .topology {
        flex-shrink: 0;
        --topology-height: 650px;
    }

    #empty {
        height: 100%;
        background-color: var(--ks-bg-elevated);
    }
</style>
