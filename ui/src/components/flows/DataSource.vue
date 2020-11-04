<template>
    <b-row>
        <b-col>
            <flow-edit v-if="flow" :prevent-route-info="true" />
        </b-col>
    </b-row>
</template>
<script>
    import {mapGetters} from "vuex";
    import FlowEdit from "./FlowEdit";
    export default {
        components: {FlowEdit},
        computed: {
            ...mapGetters("flow", ["flow"])
        }
    };
</script>