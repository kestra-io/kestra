<template>
    <Dashboard
        v-if="loaded && total"
        :restore-u-r-l="false"
        flow
        :flow-i-d="flow?.id"
        embed
    />
    <NoExecutions v-else-if="loaded && !total" />
</template>

<script setup lang="ts">
    import {computed, ref, onMounted} from "vue";
    import {useStore} from "vuex";

    import Dashboard from "../dashboard/Dashboard.vue";
    import NoExecutions from "../flows/NoExecutions.vue";

    const store = useStore();
    const flow = computed(() => store.getters["flow/flow"]);

    const total = ref(0);
    const loaded = ref(false);

    onMounted(() => {
        if (flow.value?.id) {
            store
                .dispatch("execution/findExecutions", {flowId: flow.value.id})
                .then((r) => {
                    total.value = r.total ?? 0;
                    loaded.value = true;
                });
        }
    });
</script>