<template>
    <span class="img-block">
        <img
            v-bind="$attrs"
            :alt="alt"
            :src="finalUrl"
            loading="lazy"
        >
    </span>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useDocStore} from "../../stores/doc"
    import {DOC_PATH_INJECTION_KEY} from "../docs/docsUtils"

    const docStore = useDocStore()
    const docPath = inject(DOC_PATH_INJECTION_KEY, undefined)

    const props = defineProps({
        src: {
            type: String,
            default: "",
        },
        alt: {
            type: String,
            default: "",
        },
        width: {
            type: [String, Number],
            default: undefined,
        },
        height: {
            type: [String, Number],
            default: undefined,
        },
        class: {
            type: String,
            default: "",
        },
    })

    const rawDocUrl = computed(() => docStore.resourceUrl(props.src)!)
    const finalUrl = computed(() => docPath?.value ? rawDocUrl.value.replace("/./", "/" + docPath.value + "/") : rawDocUrl.value)
</script>

<style scoped>
    .img-block {
        display: block;
        text-align: center;
    }

    img {
        max-width: 100%;
    }
</style>
