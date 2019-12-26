<template>
    <div v-if="execution" class="bg-dark log-wrapper text-white text-monospace">
        <template v-for="taskItem in execution.taskRunList">
            <template v-if="(!task || task.id === taskItem.id) && taskItem.attempts">
                <template v-for="attempt in taskItem.attempts">
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
    import {mapState} from "vuex";
    import LogLine from "./LogLine";

    export default {
        components: {LogLine},
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
            background-color: lighten($dark, 5%) ;
        }
    }
</style>