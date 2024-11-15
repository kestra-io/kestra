<script setup>
    import {computed} from "vue";
    import {useStore} from "vuex";
    import {baseUrl} from "override/utils/route";
    import {normalizeDocsPath, isRemoteLink, normalizeRemoteHref} from "../content/utils";

    const store = useStore();

    const props = defineProps({
        href: {
            type: String,
            default: undefined
        }
    });

    const polishedHref = computed(() => {
        return normalizeDocsPath(props.href);
    })

    const navigateInVuex = () => {
        store.commit("doc/setDocPath", polishedHref.value);
    };
</script>

<template>
    <a v-if="isRemoteLink(props.href)" :href="normalizeRemoteHref(props.href)" target="_blank" rel="noopener noreferrer">
        <slot />
    </a>
    <a v-else :href="`${baseUrl}/docs/${polishedHref}`" @click.prevent="navigateInVuex">
        <slot />
    </a>
</template>
