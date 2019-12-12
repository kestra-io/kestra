<template>
    <div v-if="execution">
        <b-row>
            <b-col md="6">
                <b-form-group
                    :label="$t('search term in message').capitalize()"
                    label-for="input-1"
                >
                    <b-form-input
                        id="input-1"
                        v-model="filter"
                        type="email"
                        required
                        :placeholder="$t('search') + '...'"
                    ></b-form-input>
                </b-form-group>
            </b-col>
            <b-col md="6">
                <b-form-group :label="$t('filter by log level').capitalize()" label-for="input-2">
                    <b-form-select id="input-2" v-model="level" :options="levelOptions"></b-form-select>
                </b-form-group>
            </b-col>
        </b-row>
        <b-row>
            <b-col>
                <template v-for="task in execution.taskRunList">
                    <template v-if="task.attempts">
                        <template v-for="attempt in task.attempts">
                            <template v-if="attempt.logs">
                                <template v-for="log in attempt.logs">
                                    <log-line :level="level" :filter="filterTerm" :log="log" :key="log.timestamp" />
                                </template>
                            </template>
                        </template>
                    </template>
                </template>
            </b-col>
        </b-row>
    </div>
</template>
<script>
import { mapState } from "vuex";
import LogLine from "./LogLine";
export default {
    components: { LogLine },
    data() {
        return {
            filter: "",
            level: "ALL",
            levelOptions: [
                "ALL",
                "DEBUG",
                "INFO",
                "WARNING",
                "ERROR",
                "CRITICAL"
            ]
        };
    },
    computed: {
        ...mapState("execution", ["execution"]),
        filterTerm() {
            return this.filter.toLowerCase();
        }
    }
};
</script>