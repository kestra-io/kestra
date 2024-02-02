<template>
    <div :id="uuid" class="namespace-treemap" v-if="dataReady">
        <el-tooltip
            :persistent="false"
            :hide-after="0"
            transition=""
            :popper-class="tooltipContent === '' ? 'd-none' : 'tooltip-stats'"
        >
            <template #content>
                <span v-html="tooltipContent" />
            </template>
            <TreeMapChart ref="chartRef" :chart-data="chartData" :options="options" />
        </el-tooltip>
    </div>
</template>

<script>
    import {computed, defineComponent, ref, getCurrentInstance} from "vue";
    import {useRoute, useRouter} from "vue-router"
    import Utils from "../../utils/utils.js";
    import TreeMapChart from "../../charts/TreeMapChart"
    import {defaultConfig, chartClick, backgroundFromState} from "../../utils/charts";
    import {color} from "chart.js/helpers";

    export default defineComponent({
        components: {TreeMapChart},
        props: {
            data: {
                type: Array,
                required: true
            },
        },
        setup(props) {
            const moment = getCurrentInstance().appContext.config.globalProperties.$moment;
            const route = useRoute();
            const router = useRouter();

            const chartRef = ref();
            const tooltipContent = ref("");
            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

            const dataReady = computed(() => props.data !== undefined)

            const tooltip = (context) => {
                const tooltip = context.tooltip;
                const tree = tooltip.dataPoints[0].dataset.tree;
                const namespace = tooltip.dataPoints[0].raw._data.children[0].namespace;

                let innerHtml = "";
                innerHtml += "<h6>" + namespace + "</h6>";

                const reduce = tree
                    .filter(data => data.namespace === namespace)
                    .reduce(function (accumulator, value) {
                        const state = value.state.toUpperCase();
                        if (accumulator[state] === undefined) {
                            accumulator[state] = 0
                        }

                        accumulator[state] += value.count;

                        return accumulator;
                    }, Object.create(null))

                Object.keys(reduce)
                    .map((state) => {
                        if (reduce[state] > 0) {
                            const stateColor = backgroundFromState(state);
                            const style = "background:" + stateColor + ";" +
                                "border-color:" + color(stateColor).darken(0.8).hexString();
                            let span = "<span class=\"square\" style=\"" + style + "\"></span>";
                            innerHtml += span + state + ": " + reduce[state]  + "<br />";
                        }
                    });


                tooltipContent.value = innerHtml;
            }

            const options = computed(() => defaultConfig({
                onClick: (e, elements) => {
                    if (elements.length > 0 && elements[1].index !== undefined  && elements[1].datasetIndex !== undefined ) {
                        let data = e.chart.data.datasets[elements[0].datasetIndex].data[elements[0].index];
                        let dates = data._data.children.map(r => moment(r.date).unix());
                        let states = [...new Set(data._data.children.map(r => r.state.toUpperCase()))];

                        chartClick(
                            moment,
                            router,
                            route,
                            {
                                namespace: data.g,
                                startDate: moment.unix(Math.min(...dates)).toISOString(true),
                                state: states,
                                endDate: moment.unix(Math.max(...dates)).endOf("day").toISOString(true),
                            }
                        );
                    }
                },
                plugins: {
                    tooltip: {
                        external: tooltip
                    },
                },
                elements: {
                    treemap: {
                        captions: {
                            color: (darkTheme ? "#7081b9" : "#7081b9")
                        }
                    }
                }
            }));

            return {
                chartData: {
                    datasets: [{
                        tree: props.data,
                        key: "count",
                        groups: ["namespace", "state"],
                        backgroundColor(ctx) {
                            const item = ctx.dataset.data[ctx.dataIndex];
                            const color = item ? backgroundFromState(item.g.toUpperCase()) : undefined;

                            return color !== undefined ? color : (darkTheme ? "#303e67" : "#eaf0f9");
                        },
                        spacing: 1,
                        borderWidth: 1,
                        color: "#FFFFFF",
                    }]
                },
                tooltipContent,
                chartRef,
                options,
                dataReady
            };
        },
        data() {
            return {
                uuid: Utils.uid(),
            };
        },
    });
</script>

<style lang="scss" scoped>
    .namespace-treemap {
        div {
            height: 200px;
        }
    }
</style>