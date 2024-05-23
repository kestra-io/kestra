<template>
    <el-button class="secondary">
        {{ label }}
    </el-button>
</template>

<script setup lang="ts">
    defineProps({
        label: {type: String, required: true},
    });
</script>

<style scoped lang="scss">
$background: #2f3342;

.secondary {
    padding: 1rem 2rem;
    background: $background;
}
</style>
