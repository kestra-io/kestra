<template>
    <div class="d-flex justify-content-center">
        <el-button class="decline-button" @click="emit('decline')">
            {{ t('decline') }}
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
        decline: [];
    }>();
</script>

<style scoped lang="scss">
    .decline-button {
        border-color: var(--ks-border-failed);
        color: var(--ks-content-failed);
    }
</style>
