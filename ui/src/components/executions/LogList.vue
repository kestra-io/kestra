<template>
    <div v-if="execution" class="bg-dark log-wrapper text-white text-monospace">
        <template v-for="taskItem in execution.taskRunList">
            <template v-if="(!task || task.id === taskItem.id) && taskItem.attempts">
                <template v-for="(attempt, index) in taskItem.attempts">
                    <p :key="index" class="attempt">
                        <b-badge variant="primary">{{$t('attempt') | cap}} {{index + 1}}</b-badge>
                        {{attempt.state.startDate | date('LLL:ss') }} - {{attempt.state.endDate | date('LLL:ss') }} <clock/> {{$t('Duration')}} : {{attempt.state.duration | humanizeDuration}}
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
        </template>
    </div>
</template>
<script>
import { mapState } from "vuex";
import LogLine from "./LogLine";
import Clock from "vue-material-design-icons/Clock";
export default {
    components: { LogLine, Clock },
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
    computed: {
        ...mapState("execution", ["execution", "task"])
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
}
</style>