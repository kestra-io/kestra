<template>
    <span />
</template>
<script>
    export default {
        props: {
            permission: {
                type: String,
                required: true
            },
            type: {
                type: String,
                default: undefined
            },
            detail: {
                type: Object,
                required: true
            },
        }
    };
</script>
