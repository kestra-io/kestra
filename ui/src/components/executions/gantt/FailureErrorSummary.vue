<template>
    <div class="failure-error-summary">
        <div v-if="loading" v-ks-loading="true" class="failure-error-summary__status" />
        <KsEmpty v-else-if="!text" :description="$t('failureDebugPanel.error.empty')" :imageSize="80" />
        <pre v-else class="failure-error-summary__text">{{ text }}</pre>
    </div>
</template>

<script setup lang="ts">
    import {KsEmpty} from "@kestra-io/design-system"

    defineProps<{
        text: string
        loading: boolean
    }>()
</script>

<style scoped lang="scss">
    .failure-error-summary__status {
        position: relative;
        min-height: 3rem;
    }

    .failure-error-summary__text {
        max-height: 14rem;
        margin: 0;
        overflow: auto;
        color: var(--ks-log-error);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        line-height: 1.5;
        white-space: pre-wrap;
        word-break: break-word;
    }
</style>
