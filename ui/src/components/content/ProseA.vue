<template>
    <component :is="linkType" v-bind="linkProps">
        <slot />
    </component>
</template>

<script setup>
    import {computed, toRef} from "vue";
    import {useRoute} from "vue-router";
    import {useDocsLink} from "../docs/useDocsLink";

    const route = useRoute();

    const props = defineProps({
        href: {
            type: String,
            default: ""
        },
        target: {
            type: String,
            default: undefined,
            required: false
        }
    });

    const {href, isRemote} = useDocsLink(toRef(props.href), computed(() => route.path));

    const linkType = computed(() => {
        return isRemote.value ? "a" : "router-link";
    });

    const linkProps = computed(() => {
        if (isRemote.value) {
            return {
                href: href.value,
                target: "_blank"
            };
        }

        return {
            to: href.value
        };
    });
</script>