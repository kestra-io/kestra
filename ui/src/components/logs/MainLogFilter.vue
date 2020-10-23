<template>
    <b-nav>
        <search-field ref="searchField" @onSearch="onChange" />
        <namespace-select data-type="flow" @onNamespaceSelect="onChange" />
        <date-range @onDate="onChange" />
        <log-level-selector @onChange="onChange" />
    </b-nav>
</template>
<script>
import NamespaceSelect from "../namespace/NamespaceSelect";
import SearchField from "../layout/SearchField";
import DateRange from "../layout/DateRange";
import LogLevelSelector from "./LogLevelSelector";

export default {
    components: { NamespaceSelect, DateRange, SearchField, LogLevelSelector },
    methods: {
        onChange() {
            this.$emit("onChange");
        },
    },
};
</script>