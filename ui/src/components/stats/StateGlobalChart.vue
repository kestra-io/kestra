<template>
    <div class="state-global-charts">
        <div class="title" :title="$t('last 30 days count')">
            {{ $t('last 30 days count', {count: formatedCount}) }}
        </div>
        <template v-if="hasData">
            <state-chart
                v-if="ready"
                :data="data"
                :big="big"
                :global="true"
            />
        </template>
        <template v-else>
            <b-alert variant="light" class="m-0" show>
                {{ $t('no result') }}
            </b-alert>
        </template>
    </div>
</template>

<script>
    import StateChart from "./StateChart";
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
            formatedCount() {
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
@import "../../styles/_variable.scss";

.state-global-charts {
    border: 1px solid $table-border-color;
    border-bottom: 0;
    background: $gray-100;
    position: relative;
    height: 100px;
    vertical-align: middle;

    .title {
        writing-mode: vertical-rl;
        transform: rotate(-180deg);
        margin: 0;
        padding: $spacer/2;
        border-right: 0;
        background: $gray-700;
        color: $white;
        position: absolute;
        font-size: $font-size-xs;
        height: 100%;
        width: 35px;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
    }

    .alert {
        margin-left: 35px !important;
    }

    .executions-charts {
        margin-left: 35px;
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
