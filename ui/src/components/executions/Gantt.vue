<template>
    <div v-if="execution">
        <b-row class="font-weight-bold">
            <b-col md="2" sm="12" class="date text-right">{{$moment(this.start).format('YYYY-MM-DD')}}</b-col>
            <b-col v-for="(date, i) in dates" :key="i" md="2" class="time-tick">{{date}}</b-col>
        </b-row>
        <b-row v-for="taskItem in series" :key="taskItem.id">
            <b-col
                :id="`task-title-wrapper-${taskItem.id}`"
                class="task-id text-md-right"
                md="2"
                sm="12"

                >{{taskItem.name}}
                <small
                    v-if="taskItem.task && taskItem.task.value"
                >{{taskItem.task.value | ellipsis(20)}}</small>
                <b-tooltip
                    placement="right"
                    :target="`task-title-wrapper-${taskItem.id}`"
                >{{taskItem.id}}</b-tooltip>
            </b-col>
            <b-col md="10" sm="12">
                <b-tooltip :target="`task-body-wrapper-${taskItem.id}`">{{taskItem.tooltip}}</b-tooltip>
                <div
                    :id="`task-body-wrapper-${taskItem.id}`"
                    :style="{left: taskItem.start + '%', position: 'relative', width: taskItem.width + '%', height: '20px', cursor:'pointer'}"
                    :class="taskItem.color"
                    class="task-progress"
                    @click="onTaskSelect(taskItem.task)"
                >
                    <span class="task-content">{{taskItem.text}}</span>
                </div>
                <hr />
            </b-col>
            <b-col v-if="task && task.id === taskItem.id" md="12">
                <log-list :task-run-id="task.id" level="TRACE"/>
                <br />
            </b-col>
        </b-row>
    </div>
</template>
<script>
import LogList from "./LogList";
import { mapState } from "vuex";
import humanizeDuration from "humanize-duration";

const ts = date => new Date(date).getTime();

export default {
    components: { LogList },
    data() {
        return {
            colors: {
                SUCCESS: "bg-success text-white",
                RUNNING: "bg-primary text-white",
                FAILED: "bg-danger text-white"
            },
            series: [],
            intervalHandler: undefined
        };
    },
    watch: {
        execution() {
            this.computeSeries();
        }
    },
    mounted() {
        this.intervalHandler = setInterval(this.computeSeries, 40);
    },
    computed: {
        ...mapState("execution", ["execution", "task"]),
        dates() {
            const ticks = 5;
            const date = ts => this.$moment(ts).format("h:mm:ss");
            const start = this.start;
            const delta = this.delta() / ticks;
            const dates = [];
            for (let i = 0; i < ticks; i++) {
                dates.push(date(start + i * delta));
            }
            return dates;
        },
        hasTaskLog() {
            return (
                this.task &&
                this.task.attempts &&
                this.task.attempts.length &&
                this.task.attempts[0].logs &&
                this.task.attempts[0].logs.length
            );
        },
        start() {
            return ts(this.execution.state.histories[0].date);
        }
    },
    methods: {
        delta() {
            return this.stop() - this.start;
        },
        stop() {
            if (this.execution.state.current === "RUNNING") {
                return +new Date();
            }
            const lastIndex = this.execution.state.histories.length - 1;
            return ts(this.execution.state.histories[lastIndex].date);
        },
        computeSeries() {
            if (!this.execution) {
                return;
            }
            if (
                !["RUNNING", "CREATED"].includes(this.execution.state.current)
            ) {
                this.stopRealTime();
            }
            const series = [];
            const executionDelta = this.delta(); //caching this value matters
            for (let task of this.execution.taskRunList || []) {
                const lastIndex = task.state.histories.length - 1;
                const startTs = ts(task.state.histories[0].date);
                const stopTs = ts(task.state.histories[lastIndex].date);

                const start = startTs - this.start;
                let stop = stopTs - this.start - start;

                const delta = stopTs - startTs;
                const duration = this.$moment.duration(delta);
                const humanDuration = humanizeDuration(duration);
                let width = (stop / executionDelta) * 100
                if (['CREATED', 'RUNNING'].includes(task.state.current)) {
                    width = ((this.stop() - startTs) / executionDelta) * 100 //(stop / executionDelta) * 100
                }
                if (width < 0.5) {
                    width = 0.5
                }
                series.push({
                    id: task.id,
                    name: task.taskId,
                    start: (start / executionDelta) * 100,
                    width,
                    tooltip: `${this.$t("duration")} : ${humanDuration}`,
                    color: this.colors[task.state.current],
                    task
                });
            }
            this.series = series;
        },
        onTaskSelect(task) {
            task = this.task && this.task.id === task.id ? undefined : task;
            this.$store.commit("execution/setTask", task);
        },
        stopRealTime() {
            if (this.intervalHandler) {
                clearInterval(this.intervalHandler);
            }
        }
    },
    destroyed() {
        this.stopRealTime();
    }
};
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.task-content {
    margin-left: 45%;
    font-size: 0.8em;
    bottom: 10px;
}
.task-id {
    font-family: monospace;
    height: 30px;
}
.task-progress {
    border-radius: 4px;
}

.time-tick,
.date {
    border-left: 1px solid $gray-500;
    font-size: $font-size-xs;
    padding: 0.2rem 0.4rem;
    margin-bottom: 0.2rem;
}

.date {
    border: 0;

}
</style>
