<template>
    <el-button type="primary" :class="{high, full}" class="main">
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
