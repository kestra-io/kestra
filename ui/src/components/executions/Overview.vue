<template>
    <div v-if="execution">
        <b-row class="mb-3 text-right">
            <b-col>
                <status :status="execution.state.current" />
            </b-col>
        </b-row>
        <b-table responsive="xl" striped hover bordered :items="items" class="mb-0"></b-table>
    </div>
</template>
<script>
import { mapState } from "vuex";
import Status from "../Status";
import humanizeDuration from "humanize-duration";

const ts = date => new Date(date).getTime();

export default {
    components: {
        Status
    },
    computed: {
        ...mapState("execution", ["execution"]),
        items() {
            const startTs = this.execution.state.histories[0].date;
            const stopTs = this.execution.state.histories[this.execution.state.histories.length - 1].date;
            const delta = ts(stopTs) - ts(startTs);
            const duration = this.$moment.duration(delta);
            const humanDuration = humanizeDuration(duration);
            console.log(duration);
            return [
                {key: this.$t('namespace'), value: this.execution.namespace},
                {key: this.$t('flow'), value: this.execution.flowId},
                {key: this.$t('created date'), value: startTs},
                {key: this.$t('updated date'), value: stopTs},
                {key: this.$t('duration'), value: humanDuration},
                {key: this.$t('steps'), value: this.execution.taskRunList.length},
            ]
        }
    }
};
</script>
<style scoped lang="scss">
    /deep/ thead {
        display: none;

    }
</style>