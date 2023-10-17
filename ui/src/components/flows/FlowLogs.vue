<template>
    <logs-wrapper :restore-url="false" embed />
</template>

<script>
    import LogsWrapper from "../logs/LogsWrapper.vue";

    export default {
        components: {
            LogsWrapper,
        },
    };
</script>
