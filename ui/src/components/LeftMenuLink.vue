<template>
    <a v-if="isHyperLink" v-bind="$attrs" ref="slotContainer">
        <slot />
    </a>
    <router-link v-else :to="$attrs.href as string" custom v-slot="{href:linkHref, navigate}">
        <a v-bind="$attrs" :href="linkHref" @click="navigate" ref="slotContainer">
            <EnterpriseBadge :enable="isLocked">
                <slot />
            </EnterpriseBadge>
        </a>
    </router-link>
</template>

<script setup lang="ts">
    import {computed, ref, onMounted} from "vue"
    import {useRouter} from "vue-router";
    import EnterpriseBadge from "./EnterpriseBadge.vue";

    defineOptions({
        name: "LeftMenuLink",
        inheritAttrs: false,
    })

    interface MenuItem{
        href?: string;
        external?: boolean;
        attributes?: {
            locked?: boolean;
        };
    }

    const props = defineProps<{
        item: MenuItem;
    }>()

    const router = useRouter()

    const isHyperLink = computed<boolean>(() => {
        return !!(!props.item.href || props.item.external || !router)
    })

    const isLocked = computed<boolean>(() => {
        return props.item?.attributes?.locked || false;
    })

    const slotContainer = ref<HTMLAnchorElement | null>(null)
    const term = ref<string>()

    onMounted(() => {
        if (slotContainer?.value?.innerText) {
            term.value = encodeURIComponent(slotContainer.value.innerText.trim().toLowerCase())
        }
    })
</script>