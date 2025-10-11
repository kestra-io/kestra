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

<script setup lang="ts">
    import {computed, toRef} from "vue";
    import {useDocStore} from "../../stores/doc";
    import {useDocsLink} from "./useDocsLink";

    const docStore = useDocStore();

    const emit = defineEmits<{
        click: []
    }>();

    const props = withDefaults(defineProps<{
        href?: string;
        useRaw?: boolean;
        class?: string | Record<string, boolean> | Array<string | Record<string, boolean>>;
    }>(), {
        href: undefined,
        useRaw: false,
        class: undefined
    });

    const {href, isRemote} = useDocsLink(toRef(() => props.href ?? ""), computed(() => (docStore.docPath ?? "")));
    const finalHref = computed(() => props.useRaw ? `/${props.href}` : href.value);

    const navigateInVuex = () => {
        docStore.docPath = finalHref.value;
    };
</script>
