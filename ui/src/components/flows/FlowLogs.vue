<template>
    <logs-wrapper :restore-url="false" :prevent-route-info="true" />
</template>

<script>
    import LogsWrapper from "../logs/LogsWrapper.vue";

    export default {
        components: {
            LogsWrapper,
        },
    };
</script>
