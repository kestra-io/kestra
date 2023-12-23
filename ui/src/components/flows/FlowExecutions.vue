<template>
    <executions :restore-url="false" filter :topbar="false" />
</template>

<script>
    import Executions from "../executions/Executions.vue";

    export default {
        inheritAttrs: false,
        components: {
            Executions,
        },
    };
</script>
