<template>
    <logs-wrapper :restore-url="false" :prevent-route-info="true" />
</template>

<script>
    import LogsWrapper from "../logs/LogsWrapper";

    export default {
        components: {
            LogsWrapper,
        },
    };
</script>
