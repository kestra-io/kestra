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
$width: 50%;

.secondary {
    width: $width;
    padding: 1rem 2rem;
}
</style>
