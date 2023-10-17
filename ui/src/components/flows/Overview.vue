<template>
    <div>
        <home
            v-if="flow"
            :namespace="flow.namespace"
            :flow-id="flow.id"
            :restore-url="false"
            embed
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