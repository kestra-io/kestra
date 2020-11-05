<template>
    <b-navbar toggleable="lg" type="light" variant="light">
        <b-navbar-toggle target="nav-collapse" />
        <b-collapse id="nav-collapse" is-nav>
            <b-nav-form>
                <search-field ref="searchField" @onSearch="onChange" />
                <namespace-select v-if="$route.name !== 'flowEdit'" data-type="flow" @onNamespaceSelect="onChange" />
                <log-level-selector @onChange="onChange" />
                <date-range @onDate="onChange" />
            </b-nav-form>
        </b-collapse>
    </b-navbar>
</template>
<script>
    import NamespaceSelect from "../namespace/NamespaceSelect";
    import SearchField from "../layout/SearchField";
    import DateRange from "../layout/DateRange";
    import LogLevelSelector from "./LogLevelSelector";

    export default {
        components: {NamespaceSelect, DateRange, SearchField, LogLevelSelector},
        methods: {
            onChange() {
                this.$emit("onChange");
            },
        },
    };
</script>
