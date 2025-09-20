<template>
    <Dashboard
        v-if="loaded && total && flow"
        :header="false"
        isFlow
    />
    <NoExecutions v-else-if="loaded && flow && !total" />
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue";
    import {useExecutionsStore} from "../../stores/executions";

    defineOptions({inheritAttrs: false});

    import Dashboard from "../dashboard/Dashboard.vue";
    import NoExecutions from "../flows/NoExecutions.vue";
    import {useFlowStore} from "../../stores/flow";

    const flowStore = useFlowStore();
    const flow = computed(() => flowStore.flow);
    const executionsStore = useExecutionsStore();

    const total = ref(0);
    const loaded = ref(false);

    defineEmits(["expand-subflow"]);

    onMounted(() => {
        if (flow.value?.id) {
            executionsStore
                .findExecutions({namespace: flow.value.namespace, flowId: flow.value.id})
                .then((r) => {
                    total.value = r.total ?? 0;
                    loaded.value = true;
                });
        }
    });
</script>