<template>
    <Dashboard
        v-if="loaded && total && namespace"
        :header="false"
        isNamespace
    />
    <NoExecutions v-else-if="loaded && namespace && !total" :isNamespace="true" />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useExecutionsStore} from "../../../stores/executions"
    
    import Dashboard from "../../dashboard/Dashboard.vue"
    import NoExecutions from "../../flows/NoExecutions.vue"

    const route = useRoute()
    const executionsStore = useExecutionsStore()

    const namespace = computed(() => route.params?.id as string)
    const total = ref(0)
    const loaded = ref(false)

    watch(namespace, (current) => {
        if (!current) return
        loaded.value = false
        total.value = 0
        executionsStore
            .findExecutions({"filters[namespace][PREFIX]": current, onlyTotal: true})
            .then((response) => {
                if (current !== namespace.value) return
                total.value = response as number
                loaded.value = true
            })
            .catch(() => {
                if (current === namespace.value) loaded.value = true
            })
    }, {immediate: true})
</script>
