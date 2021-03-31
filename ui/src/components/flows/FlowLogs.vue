<template>
    <logs-wrapper :restore-url="false" />
</template>

<script>
    import LogsWrapper from "../logs/LogsWrapper";

    export default {
        components: {
            LogsWrapper,
        },
    };
</script>
