<template>
    <el-button class="main alternative">
        {{ label }}
    </el-button>
</template>

<script setup lang="ts">
    defineProps({
        label: {type: String, required: true},
    });
</script>
