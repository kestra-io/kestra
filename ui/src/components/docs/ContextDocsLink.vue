<script setup>
    import {computed, toRef} from "vue";
    import {useStore} from "vuex";
    import {useDocsLink} from "./useDocsLink";

    const store = useStore();

    const emit = defineEmits(["click"]);

    const props = defineProps({
        href: {
            type: String,
            default: undefined
        },
        useRaw: {
            type: Boolean,
            default: false
        }
    });

    const {href, isRemote} = useDocsLink(toRef(props.href), computed(() => (store.getters["doc/docPath"] ?? "")));
    const finalHref = computed(() => props.useRaw ? `/${props.href}` : href.value);

    const navigateInVuex = () => {
        store.commit("doc/setDocPath", finalHref.value);
    };
</script>

<template>
    <a v-if="isRemote" :href="finalHref" @click="emit('click')" target="_blank" rel="noopener noreferrer">
        <slot />
    </a>
    <a v-else :href="`/docs${finalHref}`" @click.prevent="() => {navigateInVuex();emit('click');}">
        <slot />
    </a>
</template>
