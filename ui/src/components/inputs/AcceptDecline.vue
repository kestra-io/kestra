<template>
    <div class="d-flex justify-content-center">
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
    .reject-button {
        border-color: var(--ks-border-failed);
        color: var(--ks-content-failed);
    }
</style>
