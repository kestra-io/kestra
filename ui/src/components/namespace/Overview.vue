<template>
    <div>
        <home
            :namespace="$route.params.id || $route.query.id"
            :restore-url="false"
            :description="$route.params.id ? namespace?.description : undefined"
            embed
        />
    </div>
</template>

<script>
    import Home from "../home/Home.vue"
    import {mapState} from "vuex";

    export default {
        components: {
            Home
        },
        computed: {
            ...mapState("namespace", ["namespace"]),
        }
    }
</script>