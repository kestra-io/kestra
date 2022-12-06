<template>
    <flow-edit v-if="flow" :prevent-route-info="true" />
</template>

<script>
    import {mapGetters} from "vuex";
    import FlowEdit from "override/components/flows/FlowEdit";
    export default {
        name: "FlowSource",
        components: {FlowEdit},
        computed: {
            ...mapGetters("flow", ["flow"])
        }
    };
</script>