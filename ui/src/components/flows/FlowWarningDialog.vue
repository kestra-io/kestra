<template>
    <div class="d-flex py-3 align-items-center flow-warning-dialog">
        <AlertCircle class="me-2 icon" />
        <strong>{{ $t("warning") }}</strong>
    </div>

    <KsMarkdown :content="$t('trigger_check_warning')" />
</template>

<script setup lang="ts">
    import {KsMarkdown} from "@kestra-io/design-system"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
</script>

<style lang="scss">
.flow-warning-dialog .icon {
    font-size: var(--ks-font-size-lg);
    color: var(--ks-content-warning);
}
</style>