<template>
    <el-button type="primary" :class="{high, full}" class="primary">
        {{ label }}
    </el-button>
</template>

<script setup lang="ts">
    defineProps({
        label: {type: String, required: true},
        high: {type: Boolean, required: false},
        full: {type: Boolean, required: false},
    });
</script>

<style scoped lang="scss">
$width: 50%;
$height: 50px;
$full: 100%;

.primary {
    width: $width;
    padding: 1rem 2rem;
}

.high {
    height: $height;
}

.full {
    width: $full;
}
</style>
