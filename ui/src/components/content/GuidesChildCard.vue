<template>
    <ChildCard :pageUrl="pageUrl" />
</template>

<script setup lang="ts">
    import {defineProps} from "vue";
    import ChildCard from "./ChildCard.vue";

    defineProps<{
        pageUrl?: string;
    }>();
</script>
