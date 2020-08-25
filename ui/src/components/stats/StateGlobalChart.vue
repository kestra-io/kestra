<template>
    <div class="state-global-charts">
        <div class="title" :title="$t('last 30 days executions')">{{$t('last 30 days executions')}}</div>
        <state-chart
            v-if="ready"
            dateFormat="YYYY-MM-DD"
            :endDate="endDate"
            :startDate="startDate"
            :data="data"
            :global="true"
        />
    </div>
</template>

<script>
    import StateChart from "../stats/StateChart";

    export default {
        name: "c3-chart",
        components: {
            StateChart
        },
        props: {
            ready: {
                type: Boolean,
                required: true
            },
            startDate: {
                type: Date,
                required: true
            },
            endDate: {
                type: Date,
                required: true
            },
            data: {
                type: Array,
                required: true
            },
        },
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
        height: 100px;
        width: 35px;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
    }

    .state-charts {
        margin-left: 35px;
        top: 0;
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
    }
}
</style>
