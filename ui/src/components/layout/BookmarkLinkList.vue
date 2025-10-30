<template>
    <BookmarkLink v-for="p in pages" :key="p.path" :href="p.path" :title="p.label" />
</template>

<script setup lang="ts">
    import BookmarkLink from "./BookmarkLink.vue"

    defineProps<{
        pages: {label?:string, path:string}[]
    }>()
</script>
