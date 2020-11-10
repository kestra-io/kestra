<template>
    <b-form-select @input="searchStatus" v-model="selected" size="sm" :options="statuses" />
</template>
<script>
    import State from "../../utils/state";
    export default {        
        data() {
            let states = State.allStates().map(s => s.toLowerCase());
            return {
                statuses: ["all"].concat(states),
                selected: "all"
            };
        },
        created() {
            if (this.$route.query.status) {
                this.selected = this.$route.query.status.toLowerCase();
            }
        },
        methods: {
            searchStatus() {
                const status = this.selected.toUpperCase();
                if (this.$route.query.status !== status) {
                    this.$router.push({query: {...this.$route.query, status}});
                    this.$emit("onRefresh");
                }
            }
        }
    };
</script>
