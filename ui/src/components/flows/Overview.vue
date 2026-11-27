<template>
    <Dashboard
        v-if="loaded && total && flow"
        :header="false"
        isFlow
    />
    <NoExecutions v-else-if="loaded && flow && !total" />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useExecutionsStore} from "../../stores/executions"

    defineOptions({inheritAttrs: false})

    import Dashboard from "../dashboard/Dashboard.vue"
    import NoExecutions from "../flows/NoExecutions.vue"
    import {useFlowStore} from "../../stores/flow"

    const flowStore = useFlowStore()
    const flow = computed(() => flowStore.flow)
    const executionsStore = useExecutionsStore()

    const total = ref(0)
    const loaded = ref(false)

    defineEmits(["expand-subflow"])

    const flowKey = computed(() => flow.value?.id ? `${flow.value.namespace}/${flow.value.id}` : undefined)

    watch(flowKey, (key) => {
        if (!key || !flow.value) return
        loaded.value = false
        executionsStore
            .findExecutions({"filters[namespace][EQUALS]": flow.value.namespace, "filters[flowId][EQUALS]": flow.value.id})
            .then((r) => {
                if (key !== flowKey.value) return
                total.value = r.total ?? 0
                loaded.value = true
            })
    }, {immediate: true})
</script>
