<template>
    <KsEntityLink
        v-if="props.field"
        entity="namespace"
        :value="props.field"
        :to="{name: 'namespaces/update', params: {id: props.field}}"
        noIcon
    />
</template>

<script setup lang="ts">
    const props = defineProps({
        field: {
            type: String,
            default: undefined,
        },
    })
</script>
