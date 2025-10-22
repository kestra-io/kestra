<template>
    <component :icon="AxisYArrow" :is="component" @click="click" class="node-action" size="small">
        <span v-if="component !== 'el-button'">{{ $t('sub flow') }}</span>
    </component>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useExecutionsStore} from "../../stores/executions";
    import {useRouter, useRoute} from "vue-router";
    import AxisYArrow from "vue-material-design-icons/AxisYArrow.vue";

    const props = withDefaults(defineProps<{
        component?: string;
        executionId?: string;
        namespace?: string;
        flowId?: string;
        tabFlow?: string;
        tabExecution?: string;
    }>(), {
        component: "el-button",
        tabFlow: "overview",
        tabExecution: "topology",
        executionId: undefined,
        namespace: undefined,
        flowId: undefined
    });

    const router = useRouter();
    const route = useRoute();
    const executionsStore = useExecutionsStore();

    const routeName = computed(() => {
        return props.executionId ? "executions/update" : "flows/update"
    });

    const tab = computed(() => {
        return props.executionId ? props.tabExecution : props.tabFlow
    });

    interface Execution {
        id: string;
        namespace: string;
        flowId: string;
    }

    const params = (execution?: Execution) => {
        if (execution) {
            return {
                namespace: execution.namespace, 
                flowId: execution.flowId, 
                id: execution.id, 
                tab: tab.value
            }
        } else {
            return {
                namespace: props.namespace, 
                id: props.flowId, 
                tab: tab.value
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
                    tab: tab.value,
                    tenant: route.params.tenant
                },
            });
        } else if (props.executionId) {
            executionsStore
                .loadExecution({id: props.executionId})
                .then(value => {
                    executionsStore.execution = value;
                    router.push({name: routeName.value, params: params(value)})
                })
        } else {
            router.push({name: routeName.value, params: params()})
        }
    }

</script>