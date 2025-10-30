<template>
    <ChildCard :pageUrl />
</template>

<script setup lang="ts">
    import ChildCard from "./ChildCard.vue";

    defineProps<{ pageUrl?: string }>();
</script>
