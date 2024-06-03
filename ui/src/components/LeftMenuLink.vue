<template>
    <div v-if="isLocked" v-bind="$attrs">
        <span ref="slotContainer" class="d-none">
            <slot />
        </span>
        <enterprise-tooltip :disabled="true" :term="term" content="left-menu">
            <slot />
        </enterprise-tooltip>
    </div>
    <a v-else-if="isHyperLink" v-bind="$attrs">
        <slot />
    </a>
    <router-link v-else v-slot="{href, navigate}" custom :to="$attrs.href">
        <a v-bind="$attrs" :href="href" @click="navigate">
            <slot />
        </a>
    </router-link>
</template>

<script>
    export default {
        name: "LeftMenuLink",
        compatConfig: {
            MODE: 3,
            inheritAttrs: false,
        },
    }
</script>

<script setup>
    import {computed, getCurrentInstance, ref, onMounted} from "vue"
    import EnterpriseTooltip from "./EnterpriseTooltip.vue";

    const props = defineProps({
        item: {
            type: Object,
            required: true,
        },
    })

    const router = getCurrentInstance().appContext.config.globalProperties.$router

    const isHyperLink = computed(() => {
        return !!(!props.item.href || props.item.external || !router)
    })

    const isLocked = computed(() => {
        return props.item?.attributes?.locked || false;
    })

    const slotContainer = ref(null)
    const term = ref("undefined")

    onMounted(() => {
        if (slotContainer?.value?.innerText) {
            term.value = encodeURIComponent(slotContainer.value.innerText.trim().toLowerCase())
        }
    })
</script>
