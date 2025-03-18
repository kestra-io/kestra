<template>
    <p class="py-1 title">
        {{ text }}
    </p>
</template>

<script setup lang="ts">
    defineProps({text: {type: String, required: true}});
</script>

<style scoped lang="scss">
@import "../styles/filter";

.title {
    margin: 0;
    padding: calc(1rem / 4) 0 0 1rem;

    color: $filters-gray-700;

    font-size: $filters-font-xs;
}
</style>
