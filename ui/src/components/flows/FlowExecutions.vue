<template>
    <executions :restore-url="false" filter embed/>
</template>

<script>
    import Executions from "../executions/Executions.vue";

    export default {
        components: {
            Executions,
        },
    };
</script>
