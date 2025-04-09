<template>
    <el-button type="primary" size="small" class="ms-2 beta">
        Beta
    </el-button>
</template>

<script setup lang="ts"></script>

<style scoped lang="scss">
.beta {
    pointer-events: none;
    --el-button-border-color: var(--ks-button-content-primary);
    border-color: var(--ks-button-content-primary);
    border-radius: calc(var(--el-border-radius-round) * 2);
}
</style>
