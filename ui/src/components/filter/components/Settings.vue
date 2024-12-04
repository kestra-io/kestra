<template>
    <el-dropdown trigger="click" placement="bottom-end">
        <el-button :icon="TableCog" />

        <template #dropdown>
            <el-dropdown-menu class="py-2 settings-dropdown">
                <template v-if="settings.charts.shown">
                    <el-switch
                        :model-value="settings.charts.value"
                        @update:model-value="settings.charts.callback"
                        :active-text="t('filters.settings.show_chart')"
                        class="p-3"
                    />
                </template>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import TableCog from "vue-material-design-icons/TableCog.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    defineProps({
        settings: {
            type: Object,
            default: () => ({
                charts: {shown: false, value: false, callback: () => {}},
            }),
        },
    });
</script>

<style lang="scss">
.settings-dropdown {
    width: 200px;
}

.title {
    margin: 0;
    padding: calc(1rem / 4) 0 0 1rem;
    font-size: var(--el-font-size-extra-small);
    color: var(--bs-grey-700);
}
</style>
