<template>
    <executions :restore-url="false" :prevent-route-info="true" />
</template>

<script>
    import Executions from "../executions/Executions.vue";

    export default {
        components: {
            Executions,
        },
    };
</script>
