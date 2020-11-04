<template>
    <b-row>
        <b-col md="6">
            <b-form-group
                :label="$t('search term in message')"
                label-for="input-1"
            >
                <b-form-input
                    id="input-1"
                    v-model="filter"
                    required
                    size="sm"
                    @input="onChange"
                    :placeholder="$t('search') + '...'"
                />
            </b-form-group>
        </b-col>
        <b-col md="6">
            <b-form-group
                :label="$t('filter by log level')"
                label-for="input-level"
            >
                <log-level-selector @onChange="onChange" />
            </b-form-group>
        </b-col>
    </b-row>
</template>
<script>
    import LogLevelSelector from "./LogLevelSelector";
    export default {
        components: {LogLevelSelector},
        data() {
            return {
                filter: "",
            };
        },
        methods: {
            onChange() {
                const query = {...this.$route.query, q: this.filter, page: 1};
                this.$router.push({query});
                this.$emit("onChange");
            },
        },
    };
</script>
