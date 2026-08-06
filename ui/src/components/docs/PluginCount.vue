<template>
    <span> {{ totalPlugins }} </span>
</template>

<script setup lang="ts">
    import {usePluginsCount} from "../../composables/usePluginsCount"

    defineOptions({
        inheritAttrs: false,
    })

    const {totalPlugins} = usePluginsCount()
</script>
