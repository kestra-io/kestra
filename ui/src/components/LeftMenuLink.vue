<template>
    <a v-if="isHyperLink" v-bind="$attrs">
        <slot />
    </a>
    <router-link v-else :to="$attrs.href" custom v-slot="{href:linkHref, navigate}">
        <a v-bind="$attrs" :href="linkHref" @click="navigate">
            <enterprise-badge :enable="isLocked">
                <slot />
            </enterprise-badge>
        </a>
    </router-link>
</template>

<script setup>
    import {computed, ref, onMounted} from "vue"
    import {useRouter} from "vue-router";
    import EnterpriseBadge from "./EnterpriseBadge.vue";

    defineOptions({
        name: "LeftMenuLink",
        inheritAttrs: false,
    })

    const props = defineProps({
        item: {
            type: Object,
            required: true,
        },
    })

    const router = useRouter()

    const isHyperLink = computed(() => {
        return !!(!props.item.href || props.item.external || !router)
    })

    const isLocked = computed(() => {
        return props.item?.attributes?.locked || false;
    })

    const slotContainer = ref(null)
    const term = ref()

    onMounted(() => {
        if (slotContainer?.value?.innerText) {
            term.value = encodeURIComponent(slotContainer.value.innerText.trim().toLowerCase())
        }
    })
</script>
