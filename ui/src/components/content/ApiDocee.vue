<template>
    <rapi-doc
        v-if="ready"
        :spec-url="docStore.resourceUrl('kestra-ee.yml')"
        :color-scheme="theme"
        theme="violet"
        render-style="view"
        show-header="false"
        show-info="false"
        allow-authentication="false"
        allow-server-selection="false"
        allow-try="false"
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

<style scoped>
    rapi-doc {
        background: transparent;
        width: 100%;
        --font-regular: var(--ks-font-family-sans);
        --font-mono: var(--ks-font-family-mono);
    }

</style>
