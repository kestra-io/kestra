<template>
    <Dashboard
        v-if="loaded && total && namespace"
        :header="false"
        isNamespace
    />
    <NoExecutions v-else-if="loaded && namespace && !total" :isNamespace="true" />
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useExecutionsStore} from "../../../stores/executions"
    
    import Dashboard from "../../dashboard/Dashboard.vue"
    import NoExecutions from "../../flows/NoExecutions.vue"

    const route = useRoute()
    const executionsStore = useExecutionsStore()

    const namespace = computed(() => route.params?.id as string)
    const total = ref(0)
    const loaded = ref(false)

    onMounted(() => {
        if (namespace.value) {
            executionsStore
                .findExecutions({namespace: namespace.value, onlyTotal: true})
                .then((response) => {
                    total.value = response as number
                    loaded.value = true
                })
                .catch(() => {
                    loaded.value = true
                })
        }
    })
</script>
