<template>
    <b-form-select
        id="input-level"
        v-model="level"
        class="form-control"
        size="sm"
        @input="onChange"
        :options="levelOptions"
    />
</template>
<script>
    export default {
        data() {
            return {
                level: "INFO",
                levelOptions: [
                    "TRACE",
                    "DEBUG",
                    "INFO",
                    "WARN",
                    "ERROR",
                    "CRITICAL",
                ],
            };
        },
        methods: {
            onChange() {
                const query = {...this.$route.query, level: this.level};
                this.$router.push({query});
                this.$emit("onChange");
            },
        },
    };
</script>
