<template>
    <Executions :namespace="$route.params.id || $route.query.id" :topbar="false" :hidden="['selection','inputs','flowRevision','taskRunList.taskId']" />
</template>

<script>
    import Executions from "../executions/Executions.vue"
    import {mapState} from "vuex";

    export default {
        components: {Executions},
        computed: {
            ...mapState("namespace", ["namespace"]),
        },
    };
</script>
