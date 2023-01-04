<script setup>
    const props = defineProps({
        label: {
            type: String,
            required: true,
        },
    })
</script>

<template>
    <div v-if="label" class="label" v-html="label" />
</template>

<style lang="scss">
    @use "../../../styles/variable" as global-var;

    .vue-flow__node-cluster {
        background-color: rgba(global-var.$cyan, 0.05);
        border: 1px solid var(--bs-cyan);

        .label {
            color: var(--bs-cyan);
            text-align: center;
            font-size: var(--font-size-sm);
        }
    }
</style>

