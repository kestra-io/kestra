<template>
    <b-row>
        <b-col>
            <flow-edit v-if="flow"/>
        </b-col>
    </b-row>
</template>
<script>
import { mapState } from "vuex";
import FlowEdit from "./FlowEdit";
export default {
    components: { FlowEdit },
    computed: {
        ...mapState("flow", ["flow"])
    }
};
</script>