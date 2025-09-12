<template>
    <MetricsTable
        ref="table"
        :taskRunId="route.query.metric?.[0] ?? undefined"
        :showTask="true"
        :execution="executionsStore.execution"
    >
        <template #navbar>
            <KestraFilter
                :language="metricFilterLang"
                :placeholder="`${t('display metric for specific task')}...`"
                legacyQuery
            />
        </template>
    </MetricsTable>
</template>
<script setup lang="ts">
    import {onMounted, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import {useExecutionsStore} from "../../stores/executions";
    import {MetricFilterLanguage} from "../../composables/monaco/languages/filters/impl/metricFilterLanguage.ts";
    import MetricsTable from "../executions/MetricsTable.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    const {t} = useI18n();
    const route = useRoute();

    const table = ref<typeof MetricsTable>();

    const executionsStore = useExecutionsStore();

    const metricFilterLang = new MetricFilterLanguage(executionsStore)

    onMounted(() => {
        table.value!.loadData(table.value!.onDataLoaded);
    })
</script>