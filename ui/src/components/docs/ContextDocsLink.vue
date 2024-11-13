<script lang="ts" setup>
    import {computed} from "vue";
    import {useStore} from "vuex";

    const store = useStore();

    const props = defineProps<{
        href: string
    }>()

    const polishedHref = computed(() => {
        return props.href?.replaceAll(/(^|\/)\.\//g, "$1").replaceAll(/\d+\./g, "").replace(/\.md$/, "")
    })

    const navigateInVuex = () => {
        store.commit("doc/setDocPath", polishedHref.value);
    };


</script>

<template>
    <a v-if="props.href.startsWith('http')" :href="props.href" target="_blank" rel="noopener noreferrer">
        <slot />
    </a>
    <a v-else-if="props.href.startsWith('/')" :href="`https://kestra.io${props.href}`" target="_blank" rel="noopener noreferrer">
        <slot />
    </a>
    <a v-else :href="polishedHref" @click.prevent="navigateInVuex">
        <slot />
    </a>
</template>
