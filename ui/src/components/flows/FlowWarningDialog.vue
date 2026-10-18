<template>
    <div class="flow-warning-dialog">
        <AlertCircle class="icon" />
        <strong>{{ $t("warning") }}</strong>
    </div>

    <KsMarkdown :content="$t('trigger_check_warning')" />
</template>

<script setup lang="ts">
    import {KsMarkdown} from "@kestra-io/design-system"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
</script>

<style scoped lang="scss">
.flow-warning-dialog {
    display: flex;
    align-items: center;
    padding-block: var(--ks-spacing-4);

    .icon {
        font-size: var(--ks-font-size-lg);
        color: var(--ks-status-warning);
        margin-inline-end: var(--ks-spacing-2);
    }
}
</style>