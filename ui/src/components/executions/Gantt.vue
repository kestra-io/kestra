<template>
    <div v-if="execution">
        <h2>{{$t('namespace').capitalize()}} : {{execution.namespace}} &gt; {{$t('flow').capitalize()}} : {{execution.flowId}}</h2>
        <b-row v-for="task in series" :key="task.id">
            <b-col
                :id="`task-title-wrapper-${task.id}`"
                class="task-id text-md-right"
                md="2"
                sm="12"
            >
                {{task.name}}
                <b-tooltip placement="right" :target="`task-title-wrapper-${task.id}`">{{task.id}}</b-tooltip>
            </b-col>
            <b-col md="10" sm="12">
                <b-tooltip :target="`task-body-wrapper-${task.id}`">{{task.tooltip}}</b-tooltip>
                <div
                    :id="`task-body-wrapper-${task.id}`"
                    :style="{left: task.start + '%', position: 'relative', width: task.width + '%', height: '20px', cursor:'pointer'}"
                    :class="task.color"
                    class="task-progress"
                    @click="onTaskSelect(task)"
                >
                    <span class="task-content">{{task.text}}</span>
                </div>
                <hr />
            </b-col>
        </b-row>

        <b-row>
            <b-col offset-md="8" />
            <b-col
                :class="color"
                class="text-center"
                md="1"
                sm="4"
                v-for="(color, key) in colors"
                :key="key"
            >{{key}}</b-col>
        </b-row>

        <template v-if="task">
            <hr />
            <log-list />
        </template>
        <bottom-line>
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                </li>
            </ul>
        </bottom-line>
    </div>
</template>
<script>
import BottomLine from "../layout/BottomLine";
import LogList from "./LogList";
import FileDocument from "vue-material-design-icons/FileDocument";
import { mapState } from "vuex";

const ts = date => new Date(date).getTime();

export default {
    components: { BottomLine, FileDocument, LogList },
    data() {
        return {
            colors: {
                SUCCESS: "bg-success text-white",
                RUNNING: "bg-primary text-white",
                FAILED: "bg-danger text-white"
            }
        };
    },
    computed: {
        ...mapState("execution", ["execution", "task"]),
        start() {
            return ts(this.execution.state.histories[0].date);
        },
        stop() {
            const lastIndex = this.execution.state.histories.length - 1;
            return ts(this.execution.state.histories[lastIndex].date);
        },
        delta() {
            return this.stop - this.start;
        },
        series() {
            const series = [];
            for (let task of this.execution.taskRunList) {
                const lastIndex = task.state.histories.length - 1;
                const startTs = ts(task.state.histories[0].date);
                const stopTs = ts(task.state.histories[lastIndex].date);
                const start = startTs - this.start;
                const stop = stopTs - this.start - start;
                const delta = stopTs - startTs;
                const duration = this.$moment.duration(delta);
                const humanDuration =
                    duration.seconds() > 1
                        ? duration.humanize()
                        : delta + " ms";
                series.push({
                    id: task.id,
                    name: task.taskId,
                    start: (start / this.delta) * 100,
                    width: (stop / this.delta) * 100,
                    tooltip: `${this.$t(
                        "duration"
                    ).capitalize()} : ${humanDuration}`,
                    color: this.colors[task.state.current]
                });
            }
            return series;
        }
    },
    methods: {
        onTaskSelect(task) {
            this.$store.commit("execution/setTask", task);
        }
    },
};
</script>
<style lang="scss" scoped>
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
</style>