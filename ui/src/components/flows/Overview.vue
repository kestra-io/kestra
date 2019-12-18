<template>
    <b-row>
        <b-col>
            <pre>{{flow}}</pre>
        </b-col>
    </b-row>
</template>
<script>
import { mapState } from "vuex";

export default {
    components: {
    },
    computed: {
        ...mapState("flow", ["flow"])
    }
};
</script>