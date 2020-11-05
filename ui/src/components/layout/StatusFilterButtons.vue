<template>
    <b-form-select @input="searchStatus" v-model="selected" size="sm" :options="statuses" />
</template>
<script>
    export default {
        data() {
            return {
                statuses: ["all", "running", "success", "failed"],
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
