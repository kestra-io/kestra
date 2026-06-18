<template>
    <div class="empty-state">
        <component :is="icon ?? FilterRemoveOutlineIcon" class="empty-icon" />
        <strong v-if="title">{{ title }}</strong>
        <p v-if="$slots.default"><slot /></p>
        <template v-else>
            <p>{{ t("ks_table_empty.nothing_here") }}</p>
            <p>{{ t("ks_table_empty.adjust_filters") }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import {useI18n} from "vue-i18n"
    import FilterRemoveOutlineIcon from "vue-material-design-icons/FilterRemoveOutline.vue"

    defineProps<{
        title?: string
        icon?: Component
    }>()

    const {t} = useI18n({useScope: "global"})
</script>

<style scoped lang="scss">
    .empty-state {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        height: 100%;
        text-align: center;
        strong{
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-md);
            line-height: 2rem;
            font-weight: var(--ks-font-weight-bold);
        }
        p {
            line-height: 1rem;
            margin: 0;
            max-width: 320px;
            white-space: pre-line;
            font-size: var(--ks-font-size-2xs);
            font-weight: var(--ks-font-weight-regular);
            color: var(--ks-text-secondary);
        }

        .empty-icon{
            height: 24px;
            width: 24px;
            margin: 8px;
            color: var(--ks-icon-muted);
            :deep(.material-design-icon__svg) {
                width: 100%;
                height: 100%;
            }
        }
    }
</style>
