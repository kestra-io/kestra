<template>
    <el-dropdown trigger="click" placement="bottom-end">
        <KestraIcon :tooltip="$t('settings.label')" placement="bottom">
            <el-button
                :icon="ChartBar"
                :class="{settings: true, 'rounded-0 rounded-end': refresh}"
            />
        </KestraIcon>

        <template #dropdown>
            <el-dropdown-menu class="py-2 dropdown">
                <Title :text="t('filters.settings.label')" />
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
    import {PropType} from "vue";

    import {Buttons} from "../utils/types";

    import KestraIcon from "../../Kicon.vue";
    import Title from "../components/Title.vue";

    import {ChartBar} from "../utils/icons";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    defineProps({
        settings: {
            type: Object as PropType<Buttons["settings"]>,
            default: () => ({
                charts: {shown: false, value: false, callback: () => {}},
            }),
        },
        refresh: {
            type: Boolean,
            default: true,
        },
    });
</script>

<style scoped lang="scss">
.dropdown {
    width: 200px;
}
</style>
