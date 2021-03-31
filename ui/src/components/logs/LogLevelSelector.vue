<template>
    <b-form-select
        id="input-level"
        :value="value"
        class="form-control"
        size="sm"
        @input="onInput"
        :options="levelOptions"
    />
</template>
<script>
    export default {
        data() {
            return {
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
            value: {
                type: String,
                default: "INFO"
            }
        },
        methods: {
            onInput(value) {
                this.$emit("input", value);
            },
        },
    };
</script>
