<template>
    <el-card :header="$t('last 30 days count', {count: formattedCount})" shadow="never">
        <div class="state-global-charts" :class="{big: big}">
            <template v-if="hasData">
                <state-chart
                    v-if="ready"
                    :data="data"
                    :big="big"
                    :global="true"
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
            }
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

            .executions-charts > div {
                height: 200px;
            }
        }
    }
</style>
