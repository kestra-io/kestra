<template>
    <b-form-select @input="searchStatus" v-model="selected" :options="statuses"></b-form-select>
</template>
<script>
export default {
    data() {
        return {
            statuses: ["all", "running", "success", "failed"],
            selected: "all"
        };
    },
    methods: {
        searchStatus() {
            const token = this.selected.toUpperCase();
            if (this.$route.query.q !== token) {
                this.$emit("onRefresh", token === "ALL" ? "" : token);
            }
        }
    }
};
</script>
