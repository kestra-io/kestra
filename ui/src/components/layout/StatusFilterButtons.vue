<template>
    <b-form-select @input="onInput" :value="value" size="sm" :options="statuses" />
</template>
<script>
    import State from "../../utils/state";
    export default {
        props: {
            value: {
                type: String,
                default: undefined
            }
        },
        data() {
            let states = State.allStates().map(s => {
                return {value: s, text: s.toLowerCase().capitalize()}
            });

            return {
                statuses: [{value: undefined, text: ""}].concat(states),
            };
        },
        created() {

        },
        methods: {
            onInput(value) {
                this.$emit("input", value)
            }
        }
    };
</script>
