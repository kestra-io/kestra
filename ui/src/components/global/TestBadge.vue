<template>
    <el-tooltip :content="$t('test-badge-tooltip')" placement="top">
        <div style="display: inline-block;">
            <el-button type="primary" size="small" class="ms-2 test">
                {{ $t('test-badge-text') }}
            </el-button>
        </div>
    </el-tooltip>
</template>

<script setup lang="ts"></script>

<style scoped lang="scss">
.test {
    pointer-events: none;
    --el-button-border-color: var(--ks-button-content-primary);
    border-color: var(--ks-button-content-primary);
    border-radius: calc(var(--el-border-radius-round) * 2);
}
</style>
