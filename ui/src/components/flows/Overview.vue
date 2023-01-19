<template>
    <div>
        <home
            v-if="flow"
            :namespace="flow.namespace"
            :flow-id="flow.id"
            :prevent-route-info="true"
            :restore-url="false"
        />
    </div>
</template>

<script>
    import Home from "../home/Home.vue";
    import {mapGetters} from "vuex";

    export default {
        components: {
            Home
        },
        computed: {
            ...mapGetters("flow", ["flow"]),
        }
    }
</script>