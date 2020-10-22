<template>
    <log-list v-if="task" :task-run-id="task.id" level="TRACE" />
    <p v-else>logs!</p>
</template>

<script>
import LogList from "../logs/LogList";

import RouteContext from "../../mixins/routeContext";
export default {
    mixins: [RouteContext],
    components: {LogList},
    data () {
        return {
            task: undefined
        }
    },
    computed: {
        routeInfo() {
            return {
                title: this.$t("logs"),
            };
        },
    },
};
</script>
