<template>
    <div class="failure-error-summary">
        <div v-if="loading" v-ks-loading="true" class="failure-error-summary__status" />
        <KsEmpty v-else-if="!text" :description="$t('gantt_panel.no_error')" :imageSize="80" />
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

<style scoped>
    .failure-error-summary__status {
        position: relative;
        min-height: 3rem;
    }

    .failure-error-summary__text {
        max-height: 14rem;
        margin: 0;
        padding: 0;
        overflow: auto;
        border: 0;
        background: transparent;
        color: var(--ks-log-error);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        line-height: 1.5;
        white-space: pre-wrap;
        overflow-wrap: anywhere;
    }
</style>
