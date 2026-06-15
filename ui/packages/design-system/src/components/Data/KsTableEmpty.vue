<template>
    <div class="empty-state">
        <FilterRemoveOutlineIcon class="empty-icon" />
        <strong v-if="title">{{ title }}</strong>
        <p>{{ t("ks_table_empty.nothing_here") }}</p>
        <p>{{ t("ks_table_empty.adjust_filters") }}</p>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import FilterRemoveOutlineIcon from "vue-material-design-icons/FilterRemoveOutline.vue"

    defineProps<{
        title?: string
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
        strong{
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-sm);
            line-height: 2rem;
            font-weight: 600;
        }
        p {
            line-height: 1rem;
            margin: 0;
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-secondary);
        }

        .empty-icon{
            height: 24px;
            width: 24px;
            margin: 8px;
            :deep(.material-design-icon__svg) {
                width: 100%;
                height: 100%;
            }
        }
    }
</style>
