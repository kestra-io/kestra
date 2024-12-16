<template>
    <p class="py-1 title">
        {{ text }}
    </p>
</template>

<script setup lang="ts">
    defineProps({text: {type: String, required: true}});
</script>

<style scoped lang="scss">
.title {
    margin: 0;
    padding: calc(1rem / 4) 0 0 1rem;
    font-size: var(--el-font-size-extra-small);
    color: var(--bs-grey-700);
}
</style>
