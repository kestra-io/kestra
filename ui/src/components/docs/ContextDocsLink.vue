<template>
    <a v-if="isRemote" :class="props.class" :href="finalHref" @click="emit('click')" target="_blank" rel="noopener noreferrer">
        <slot />
    </a>
    <RouterLink
        v-else
        :to="{name:'docs/view', params: {path: finalHref.replace(/^\//, '')}}"
        custom
        v-slot="{href:linkHref}"
    >
        <a
            :href="linkHref"
            :class="props.class"
            @click.prevent="() => {navigateInVuex();emit('click');}"
        >
            <slot />
        </a>
    </RouterLink>
</template>

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
        },
        "class": {
            type: String,
            default: undefined
        }
    });

    const {href, isRemote} = useDocsLink(toRef(props.href), computed(() => (store.getters["doc/docPath"] ?? "")));
    const finalHref = computed(() => props.useRaw ? `/${props.href}` : href.value);

    const navigateInVuex = () => {
        store.commit("doc/setDocPath", finalHref.value);
    };
</script>
