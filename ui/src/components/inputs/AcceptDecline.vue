<template>
    <div class="wrapper">
        <el-button class="reject-button" @click="emit('reject')">
            {{ t('reject') }}
        </el-button>
        <el-button type="success" class="accept-button" @click="emit('accept')">
            {{ t('accept') }}
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import {getCurrentInstance} from "vue";

    const t = getCurrentInstance()!.appContext.config.globalProperties.$t;
    const emit = defineEmits<{
        accept: [];
        reject: [];
    }>();
</script>

<style scoped lang="scss">
.wrapper {
    width: 100%;
    display: flex;
    justify-content: end;
    align-items: center;
    gap: 10px;
    background: #1E202AD9;
    backdrop-filter: blur(10px);
    padding: 1rem;

    html.light & {
        background: #F8F9FA80;
    }

    .el-button--success, .reject-button {
        padding: 4px 12px;
        margin: 0;
    }
    
    .reject-button {
        border-color: var(--ks-border-failed);
        color: var(--ks-content-failed);
    }
}
</style>