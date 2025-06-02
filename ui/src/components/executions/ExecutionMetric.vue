<template>
    <MetricsTable
        ref="table"
        :task-run-id="$route.query.metric?.[0] ?? undefined"
        :show-task="true"
        :execution="execution"
    >
        <template #navbar>
            <KestraFilter
                :language="MetricFilterLanguage"
                :placeholder="`${$t('display metric for specific task')}...`"
                legacy-query
            />
        </template>
    </MetricsTable>
</template>
<script setup lang="ts">
    import {useStore} from "vuex";
    import MetricFilterLanguage from "../../composables/monaco/languages/filters/impl/metricFilterLanguage.ts";
    import MetricsTable from "../executions/MetricsTable.vue";
    import KestraFilter from "../filter/KestraFilter.vue";
    import {onMounted, ref} from "vue";

    const table = ref<typeof MetricsTable>();

    const store = useStore();
    const execution = store.state["execution"].execution;

    onMounted(() => {
        table.value!.loadData(table.value!.onDataLoaded);
    })
</script>