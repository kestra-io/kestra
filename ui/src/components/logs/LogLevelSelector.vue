<template>
    <b-form-select
        id="input-level"
        :value="value"
        class="form-control"
        @input="onInput"
        :options="levelOptions"
    />
</template>
<script>
    export default {
        emits: ["input"],
        data() {
            return {
                levelOptions: [
                    "TRACE",
                    "DEBUG",
                    "INFO",
                    "WARN",
                    "ERROR",
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
