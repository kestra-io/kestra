<template>
    <component :is="linkType" v-bind="linkProps">
        <slot />
    </component>
</template>

<script setup lang="ts">
    import {computed, toRef} from "vue";
    import {useRoute} from "vue-router";
    import {useDocsLink} from "../docs/useDocsLink";

    const route = useRoute();

    const props = withDefaults(defineProps<{
        href?: string;
        target?: string;
    }>(), {
        href: "",
        target: undefined
    });

    const {href, isRemote} = useDocsLink(
        toRef(props, "href"),
        computed(() => route.path)
    );

    const linkType = computed(() => {
        return isRemote.value ? "a" : "router-link";
    });

    const linkProps = computed(() => {
        if (isRemote.value) {
            return {
                href: href.value,
                target: props.target ?? "_blank"
            };
        }

        return {
            to: href.value
        };
    });
</script>