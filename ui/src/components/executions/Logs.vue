<template>
    <div>
        <b-row>
            <b-col md="6">
                <b-form-group
                    :label="$t('search term in message')"
                    label-for="input-1"
                >
                    <b-form-input
                        id="input-1"
                        v-model="filter"
                        required
                        @input="onSearch"
                        :placeholder="$t('search') + '...'"
                    ></b-form-input>
                </b-form-group>
            </b-col>
            <b-col md="6">
                <b-form-group :label="$t('filter by log level')" label-for="input-2">
                    <b-form-select id="input-2" v-model="level" :options="levelOptions"></b-form-select>
                </b-form-group>
            </b-col>
        </b-row>
        <log-list :filter="filterTerm" :level="level"/>
    </div>
</template>
<script>
import LogList from "./LogList";
import {mapState} from "vuex";
export default {
    components: { LogList },
    data() {
        return {
            filter: "",
            level: "INFO",
            levelOptions: [
                "TRACE",
                "DEBUG",
                "INFO",
                "WARN",
                "ERROR",
                "CRITICAL",
            ]
        };
    },
    created() {
        if (this.$route.query.search) {
            this.filter = this.$route.query.search || "";
        }
    },
    computed: {
        ...mapState("execution", ["execution", "task", "logs"]),
        filterTerm() {
            return this.filter.toLowerCase();
        }
    },
    watch: {
        $route() {
            if (this.$route.query.search !== this.filter) {
                this.filter = this.$route.query.search || "";
            }
        }
    },
    methods: {
        onSearch() {
            if (this.$route.query.search !== this.filter) {
                const newRoute = { query: { ...this.$route.query } };
                newRoute.query.search = this.filter;
                this.$router.push(newRoute);
            }
        },
    }
};
</script>
