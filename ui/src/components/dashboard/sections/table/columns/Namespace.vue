<template>
    <RouterLink :to="{name: 'namespaces/update', params: {id: props.field}}">
        <code class="link">{{ props.field }}</code>
    </RouterLink>
</template>

<script setup lang="ts">
    const props = defineProps({
        field: {
            type: String,
            default: undefined,
        },
    });
</script>
