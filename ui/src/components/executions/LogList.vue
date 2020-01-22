<template>
    <div v-if="execution" class="bg-dark log-wrapper text-white text-monospace">
        <template v-for="taskItem in execution.taskRunList">
            <template v-if="(!task || task.id === taskItem.id) && taskItem.attempts">
                <template v-for="(attempt, index) in taskItem.attempts">
                    <p :key="index" class="attempt">
                        <b-tooltip
                            placement="left"
                            :target="`output-toggle-${taskItem.id}`"
                            triggers="hover"
                        >{{$t('toggle output display') | cap}}</b-tooltip>
                        <b-button
                            :id="`output-toggle-${taskItem.id}`"
                            variant="info"
                            class="output"
                            size="sm"
                            @click="toggleShowOutput(taskItem)"
                        >
                            <eye />
                        </b-button>
                        <b-badge variant="primary">{{$t('attempt') | cap}} {{index + 1}}</b-badge>
                        {{attempt.state.startDate | date('LLL:ss') }} - {{attempt.state.endDate | date('LLL:ss') }}
                        <clock />
                        {{$t('Duration')}} : {{attempt.state.duration | humanizeDuration}}
                    </p>
                    <template v-if="attempt.logs">
                        <template v-for="log in attempt.logs">
                            <log-line
                                :level="level"
                                :filter="filter"
                                :log="log"
                                :key="log.timestamp"
                            />
                        </template>
                    </template>
                </template>
            </template>
            <pre :key="taskItem.id" v-if="showOutputs[taskItem.id]">{{taskItem.outputs.return}}</pre>
        </template>
    </div>
</template>
<script>
import { mapState } from "vuex";
import LogLine from "./LogLine";
import Clock from "vue-material-design-icons/Clock";
import Eye from "vue-material-design-icons/Eye";
export default {
    components: { LogLine, Clock, Eye },
    props: {
        level: {
            type: String,
            default: "ALL"
        },
        filter: {
            type: String,
            default: ""
        }
    },
    data() {
        return {
            showOutputs: {}
        };
    },
    computed: {
        ...mapState("execution", ["execution", "task"])
    },
    methods: {
        toggleShowOutput(task) {
            this.showOutputs[task.id] = !this.showOutputs[task.id];
            this.$forceUpdate();
        }
    }
};
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.log-wrapper {
    padding: 10px;
    border-radius: 5px;

    .line:nth-child(odd) {
        background-color: lighten($dark, 5%);
    }
    p.attempt {
        margin-top: $paragraph-margin-bottom;
        margin-bottom: $paragraph-margin-bottom/2;
        border-bottom: 1px solid $gray-600;
        font-family: $font-family-sans-serif;
        font-size: $font-size-base;
        padding-bottom: $paragraph-margin-bottom/2;

        .badge {
            font-size: $font-size-base;
            font-weight: bold;
            margin-right: 5px;
        }
    }
    p:first-child {
        margin-top: 0;
    }
    .output {
        margin-right: 5px;
    }
    pre {
        border: 1px solid $light;
        color: $light;
        padding: 10px;
        margin-top: 10px;
        margin-bottom: 5px;
    }
}
</style>