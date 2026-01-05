<template>
    <div class="wrapper">
        <el-button type="secondary" @click="emit('reject')">
            {{ $t("reject") }}
        </el-button>
        <el-button type="primary" @click="emit('accept')">
            {{ $t("accept") }}
        </el-button>
    </div>
</template>

<script setup lang="ts">
    const emit = defineEmits<{ accept: []; reject: [] }>();
</script>

<style scoped lang="scss">
.wrapper {
    width: 100%;
    display: flex;
    justify-content: end;
    align-items: center;
    gap: 10px;
    background: #1e202ad9;
    backdrop-filter: blur(10px);
    padding: 1rem;
    border-top: 1px solid var(--ks-border-primary);

    html.light & {
        background: #f8f9fa80;
    }

    &:deep(.el-button) {
        margin: 0;
        padding: 4px 12px;
    }
}
</style>
