<template>
    <b-row>
        <b-col md="6">
            <b-form-group
                :label="$t('search term in message')"
                label-for="input-1"
            >
                <b-form-input
                    id="input-1"
                    :value="filter"
                    required
                    @input="onChange('filter', $event)"
                    :placeholder="$t('search') + '...'"
                />
            </b-form-group>
        </b-col>
        <b-col md="6">
            <b-form-group
                :label="$t('filter by log level')"
                label-for="input-level"
            >
                <log-level-selector :value="level" @input="onChange('level', $event)" />
            </b-form-group>
        </b-col>
    </b-row>
</template>
<script>
    import LogLevelSelector from "./LogLevelSelector";
    export default {
        components: {LogLevelSelector},
        props: {
            filter: {
                type: String,
                default: undefined
            },
            level: {
                type: String,
                default: "INFO"
            }
        },
        methods: {
            onChange(type, value) {
                this.$emit("input", {[type]: value});
            },
        },
    };
</script>
