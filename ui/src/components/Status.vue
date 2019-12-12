<template>
    <span class="status" :class="cls">{{s}}</span>
</template>
<script>
export default {
    props: {
        status: {
            type: String,
            required: true
        }
    },
    computed: {
        cls() {
            return {
                Success: "bg-success text-white",
                Running: "bg-primary text-white",
                Failed: "bg-danger text-white"
            }[this.s];
        },
        s () {
            return this.status.toLowerCase().capitalize()
        }
    }
};
</script>
<style scoped>
.status {
    padding-left: 10px;
    padding-right: 10px;
    padding-top: 5px;
    padding-bottom: 5px;
    border-radius: 5px;
}
</style>