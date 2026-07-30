<template>
    <KsTooltip v-if="issues.length" :persistent="false">
        <template #content>
            <div class="block-error-tooltip">
                <div class="block-error-tooltip-head">
                    <AlertCircle :size="14" />
                    <span>{{ t("error detected") }}</span>
                </div>
                <ul class="block-error-tooltip-list">
                    <li v-for="issue in issues" :key="issue">{{ issue }}</li>
                </ul>
            </div>
        </template>
        <span
            class="block-error-badge"
            data-test="block-card-warning"
            :aria-label="t('flow_editor_stats.errors.label', {count: issues.length})"
        >
            <AlertCircle :size="14" />
            <span v-if="issues.length > 1" class="block-error-badge-count">{{ issues.length }}</span>
        </span>
    </KsTooltip>
</template>

<style scoped lang="scss">
    .block-error-badge {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: 0 var(--ks-spacing-1);
        height: 1.125rem;
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-error);
        color: var(--ks-text-error);
        cursor: help;
    }

    .block-error-badge-count {
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        line-height: 1;
        font-variant-numeric: tabular-nums;
    }

    .block-error-tooltip {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        max-width: 22rem;
    }

    .block-error-tooltip-head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        color: var(--ks-text-error);
        font-weight: 600;
        font-size: var(--ks-font-size-sm);
    }

    .block-error-tooltip-list {
        margin: 0;
        padding-left: var(--ks-spacing-4);
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-mono);
    }
</style>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"

    defineProps<{issues: string[]}>()

    const {t} = useI18n()
</script>
