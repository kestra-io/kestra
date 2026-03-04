<template>
    <span class="text-center d-block img-block">
        <img
            v-bind="$attrs"
            :alt="alt"
            :src="finalUrl"
            loading="lazy"
        >
    </span>
</template>

<script setup lang="ts">
    import {useDocStore} from "../../stores/doc";
    import {computed} from "vue";
    
    const docStore = useDocStore();

    const props = defineProps({
        src: {
            type: String,
            default: ""
        },
        alt: {
            type: String,
            default: ""
        },
        width: {
            type: [String, Number],
            default: undefined
        },
        height: {
            type: [String, Number],
            default: undefined
        },
        class: {
            type: String,
            default: ""
        }
    });

    const rawDocUrl = computed(() => docStore.resourceUrl(props.src)!);
    const finalUrl = computed(() => docStore.docPath ? rawDocUrl.value.replace("/./", "/" + docStore.docPath + "/") : rawDocUrl.value);
</script>

<style scoped lang="scss">
    img {
        max-width: 100%;
    }
</style>
