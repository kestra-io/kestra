<template>
    <left-menu v-if="miscStore.configs" @menu-collapse="onMenuCollapse" />
    <main>
        <errors v-if="error" :code="error" />
        <slot v-else />
    </main>
</template>

<script setup>
    import LeftMenu from "override/components/LeftMenu.vue";
    import Errors from "../errors/Errors.vue";
    import {useCoreStore} from "../../stores/core";
    import {useMiscStore} from "override/stores/misc";
    import {computed, onMounted} from "vue";

    const coreStore = useCoreStore();
    const miscStore = useMiscStore();
    const error = computed(() => coreStore.error);

    function onMenuCollapse(collapse) {
        document.getElementsByTagName("html")[0].classList.add(!collapse ? "menu-not-collapsed" : "menu-collapsed");
        document.getElementsByTagName("html")[0].classList.remove(collapse ? "menu-not-collapsed" : "menu-collapsed");
    }

    onMounted(() => {
        onMenuCollapse(localStorage.getItem("menuCollapsed") === "true")
    });
</script>