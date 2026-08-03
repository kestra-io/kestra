<template>
    <rapi-doc
        v-if="ready"
        :specUrl="docStore.resourceUrl('kestra.yml')"
        :theme="theme"
        renderStyle="view"
        showHeader="false"
        showInfo="false"
        allowAuthentication="false"
        allowServerSelection="false"
        allowTry="false"
        regularFont="Inter"
        monoFont="JetBrains Mono"
    />
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {useDocStore} from "../../stores/doc"
    import {getTheme} from "../../utils/utils"

    const docStore = useDocStore()
    const ready = ref(false)
    // @ts-expect-error rapidoc is not typed
    import("rapidoc").then(() => {
        ready.value = true
    })


    const theme = ref(getTheme())
</script>

<style scoped lang="scss">
    rapi-doc {
        background: transparent;
        width: 100%;
    }
</style>
