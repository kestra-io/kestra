<template>
    <el-table v-if="generated.length" :data="generated">
        <el-table-column
            v-for="(column, index) in Object.keys(props.chart.data.columns)"
            :key="index"
            :label="column"
        >
            <template #default="scope">
                {{ scope.row[column] }}
            </template>
        </el-table-column>
    </el-table>

    <NoData v-else :text="t('custom_dashboard_empty')" />
</template>

<script lang="ts" setup>
    import {onMounted, computed, ref} from "vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import NoData from "../../../../layout/NoData.vue";

    import {useStore} from "vuex";
    const store = useStore();

    defineOptions({inheritAttrs: false});
    const props = defineProps({chart: {type: Object, required: true}});

    const dashboard = computed(() => store.state.dashboard.dashboard);

    const generated = ref([]);
    onMounted(async () => {
        generated.value = await store.dispatch("dashboard/generate", {
            id: dashboard.value.id,
            chartId: props.chart.id,
            startDate: "2024-08-21T09:00:00Z",
            endDate: "2024-11-26T16:00:00Z",
        });
    });
</script>

<style lang="scss" scoped>
$height: 200px;

.chart {
    max-height: $height;
}
</style>
