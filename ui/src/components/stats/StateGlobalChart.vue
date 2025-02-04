<template>
    <el-card :header="header" shadow="never">
        <div class="state-global-charts" :class="{big: big}">
            <template v-if="hasData">
                <!-- TODO: Replace the usage of StateChart with one of the new chart components -->
                <state-chart
                    v-if="ready"
                    :data="data"
                    :big="big"
                    :global="true"
                    :namespace="namespace"
                    :flow-id="flowId"
                />
            </template>
            <template v-else>
                <el-alert type="info" :closable="false" class="m-0">
                    {{ $t('no result') }}
                </el-alert>
            </template>
        </div>
    </el-card>
</template>

<script>
    import StateChart from "./StateChart.vue";
    import Utils from "../../utils/utils";
    import {stateGlobalChartTypes} from "../../utils/constants";
    export default {
        components: {
            StateChart
        },
        props: {
            ready: {
                type: Boolean,
                required: true
            },
            data: {
                type: Array,
                required: true
            },
            big: {
                type: Boolean,
                default: false
            },
            startDate: {
                type: String,
                default: undefined
            },
            endDate: {
                type: String,
                default: undefined
            },
            type: {
                type: String,
                default: stateGlobalChartTypes.EXECUTIONS
            },
            namespace: {
                type: String,
                required: false,
                default: undefined
            },
            flowId: {
                type: String,
                required: false,
                default: undefined
            },
        },
        computed: {
            formattedCount() {
                return Utils.number(this.count);
            },
            count() {
                return [...this.data].reduce((a, b) => {
                    return a + Object.values(b.executionCounts).reduce((a, b) => a + b, 0);
                }, 0);
            },
            hasData() {
                return this.count > 0;
            },
            daysCount() {
                if (this.startDate && this.endDate) {
                    return this.$moment(this.endDate).diff(this.$moment(this.startDate), "days") + 1;
                }
                return 31;
            },
            header() {
                return this.formattedCount + " " + this.$t(this.type);
            }
        }
    };
</script>

<style lang="scss">
    .state-global-charts {
        position: relative;
        height: 100px;
        vertical-align: middle;


        .executions-charts {
            user-select: none;
            top: 0;
            height: 100%;
            display: flex;
            flex-direction: column;
            justify-content: space-evenly;
        }

        &.big {
            height: 200px;
        }
    }
</style>
