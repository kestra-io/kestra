<template>
    <div class="d-flex py-3 align-items-center flow-warning-dialog">
        <AlertCircle class="me-2 icon" />
        <strong>{{ t("warning") }}</strong>
    </div>

    <Markdown :source="t('trigger_check_warning')" />
</template>

<script setup lang="ts">
    import Markdown from "../layout/Markdown.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});
</script>

<style lang="scss">
.flow-warning-dialog .icon {
    font-size: var(--font-size-lg);
    color: var(--ks-content-warning);
}
</style>