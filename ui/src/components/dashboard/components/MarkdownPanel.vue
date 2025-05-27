<template>
    <template v-if="source">
        <section id="markdown">
            <Markdown :source />
        </section>
    </template>

    <NoData v-else :text="t('custom_dashboard_empty')" />
</template>

<script setup lang="ts">
    import {onMounted, ref, watch} from "vue";

    import Markdown from "../../layout/Markdown.vue";
    import NoData from "../../layout/NoData.vue";

    import {useRoute} from "vue-router";

    const route = useRoute();

    import {useStore} from "vuex";

    const store = useStore();

    import {useI18n} from "vue-i18n";

    const {t} = useI18n({useScope: "global"});

    import {decodeSearchParams} from "../../filter/utils/helpers.ts";

    const props = defineProps({
        chart: {type: Object, required: true},
        showDefault: {type: Boolean, default: false}
    });

    const source = ref();
    const generate = async (id) => {
        let decodedParams = decodeSearchParams(route.query, undefined, []);
        if (!props.showDefault) {
            let params = {id, chartId: props.chart.id};
            if (decodedParams) {
                params = {...params, filters: decodedParams};
            }
            const result = await store.dispatch("dashboard/generate", params);
            const description = result.results?.[0]?.description;

            source.value = description ? description : t("dashboard.no_flow_description");
        } else {
            const result = await store.dispatch("dashboard/chartPreview", {
                chart: props.chart.content,
                globalFilter: {filter: decodedParams},
            })
            source.value = result.results[0]?.description;
        }
    };

    watch(route, async (r) => {
        if (props.chart.source?.type === "FlowDescription") generate(r.params?.id);
        else source.value = props.chart.content ?? props.chart.source.content;
    });

    onMounted(() => {
        if (props.chart.source?.type === "FlowDescription") generate(route.params?.id);
        else source.value = props.chart.content ?? props.chart.source.content;
    });
</script>
