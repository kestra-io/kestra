<template>
    <b-form-select @input="searchStatus" v-model="selected" size="sm" :options="statuses" />
</template>
<script>
    import State from "../../utils/state";
    export default {
        data() {
            let states = State.allStates().map(s => {
                return {value: s, text: s.toLowerCase().capitalize()}
            });

            return {
                statuses: [{value: undefined, text: ""}].concat(states),
                selected: undefined
            };
        },
        created() {
            if (this.$route.query.status) {
                this.selected = this.$route.query.status;
            }
        },
        methods: {
            searchStatus() {
                const status = this.selected;
                if (this.$route.query.status !== status) {
                    if (status) {
                        this.$router.push({query: {...this.$route.query, status}});
                    } else {
                        let query = this.$route.query
                        delete query["status"]
                        this.$router.push({query: query});
                    }
                    this.$emit("onRefresh");
                }
            }
        }
    };
</script>
