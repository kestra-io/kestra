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
        props: {
            router: {
                type: Boolean,
                default: true
            },
            logLevel: {
                type: String,
                default: "INFO"
            }
        },
        created() {
            this.level = this.logLevel;
        },
        methods: {
            onChange() {
                if (this.router) {
                    const query = {...this.$route.query, level: this.level};
                    this.$router.push({query});
                }

                this.$emit("onChange", this.level);
            },
        },
    };
</script>
