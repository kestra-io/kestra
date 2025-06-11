<template>
    <template v-if="generated">
        <section id="kpi">
            <span class="pb-2">{{ getChartTitle(props.chart) }}</span>
            <p class="m-0 fs-2 fw-bold">
                <span>{{ generated?.results[0]?.value }}</span>
                <span v-if="percentage">%</span>
            </p>
        </section>
    </template>

    <NoData v-else :text="t('dashboards.empty')" />
</template>

<script setup lang="ts">
    import {onMounted, computed, ref, watch} from "vue";

    import NoData from "../../layout/NoData.vue";
    import {getChartTitle} from "../composables/useDashboards";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {decodeSearchParams} from "../../filter/utils/helpers.ts";

    const props = defineProps({
        chart: {type: Object, required: true},
        showDefault: {type: Boolean, default: false},
        defaultFilters: {type: Array, default: () => []},
    });

    const percentage = computed(
        () => props.chart?.chartOptions?.numberType === "PERCENTAGE"
    );

    const generated = ref();
    const generate = async (id) => {
        let decodedParams = decodeSearchParams(route.query, undefined, []);
        if (!props.showDefault) {
            let params = {
                id,
                chartId: props.chart.id,
                filters: props.defaultFilters.concat(decodedParams?? [])
            };
            generated.value = await store.dispatch("dashboard/generate", params);
        } else {
            generated.value = await store.dispatch("dashboard/chartPreview", {
                chart: props.chart.content,
                globalFilter: {filters: props.defaultFilters.concat(decodedParams?? [])},
            });
        }
    };

    watch(route, async (route) => await generate(route.params?.id));
    onMounted(() => generate(route.params.id));
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

section#kpi {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    text-align: center;
}
</style>
