<template>
    <component :icon="AxisYArrow" :is="component" @click="click" class="node-action" size="small">
        <span v-if="component !== 'KsButton'">{{ $t('sub flow') }}</span>
    </component>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {useRouter, useRoute} from "vue-router"
    import AxisYArrow from "vue-material-design-icons/AxisYArrow.vue"

    const props = withDefaults(defineProps<{
        component?: string;
        executionId?: string;
        namespace?: string;
        flowId?: string;
        tabFlow?: string;
        tabExecution?: string;
    }>(), {
        component: "KsButton",
        tabFlow: "overview",
        tabExecution: "overview",
        executionId: undefined,
        namespace: undefined,
        flowId: undefined,
    })

    const router = useRouter()
    const route = useRoute()
    const executionsStore = useExecutionsStore()

    const tab = computed(() => {
        return props.executionId ? props.tabExecution : props.tabFlow
    })

    // Executions detail is router-view driven (child route per tab); flows detail
    // still uses the legacy `:tab?` param, so only the execution branch bakes the
    // tab into the route name.
    const routeName = computed(() => {
        return props.executionId ? `executions/update/${tab.value}` : "flows/update"
    })

    const params = (execution?: Execution) => {
        if (execution) {
            return {
                namespace: execution.namespace,
                flowId: execution.flowId,
                id: execution.id,
            }
        } else {
            return {
                namespace: props.namespace,
                id: props.flowId,
                tab: tab.value,
            }
        }
    }

    const click = () => {
        if (props.executionId && props.namespace && props.flowId) {
            router.push({
                name: routeName.value,
                params: {
                    namespace: props.namespace,
                    flowId: props.flowId,
                    id: props.executionId,
                    tenant: route.params.tenant,
                },
            })
        } else if (props.executionId) {
            executionsStore
                .loadExecution({id: props.executionId})
                .then(value => {
                    executionsStore.execution = value
                    router.push({name: routeName.value, params: params(value)})
                })
        } else {
            router.push({name: routeName.value, params: params()})
        }
    }

</script>