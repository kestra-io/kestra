<template>
    <ChildCard :page-url="pageUrl" />
</template>

<script>
    import ChildCard from "./ChildCard.vue";

    export default {
        components: {ChildCard},
        props: {
            pageUrl: {
                type: String,
                default: undefined
            }
        }
    }
</script>