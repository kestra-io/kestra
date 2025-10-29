<template>
    <span>
        <router-link :to="{name: 'executions/update', params: {id: props.execution, namespace: props.namespace, flowId: props.flowId}}">
            {{ props.execution }}
        </router-link>
        {{ $t('for flow') }}
        <router-link :to="{name: 'flows/update', params: {namespace: props.namespace, id: props.flowId}}">
            {{ props.namespace }}.{{ props.flowId }}
        </router-link>
    </span>
</template>

<script setup>
    const props = defineProps({
        execution: {
            type: String,
            required: true
        },
        flowId: {
            type: String,
            required: true
        },
        namespace: {
            type: String,
            required: true
        }
    })
</script>