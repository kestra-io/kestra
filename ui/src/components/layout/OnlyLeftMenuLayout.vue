<template>
    <left-menu v-if="configs" @menu-collapse="onMenuCollapse" />
    <main>
        <errors v-if="error" :code="error" />
        <slot v-else />
    </main>
</template>

<script setup>
    import LeftMenu from "override/components/LeftMenu.vue";
    import Errors from "../errors/Errors.vue";
    import {useStore} from "vuex";
    import {computed, onMounted} from "vue";

    const store = useStore();
    const configs = computed(() => store.getters["misc/configs"]);
    const error = computed(() => store.getters["core/error"]);

    function onMenuCollapse(collapse) {
        document.getElementsByTagName("html")[0].classList.add(!collapse ? "menu-not-collapsed" : "menu-collapsed");
        document.getElementsByTagName("html")[0].classList.remove(collapse ? "menu-not-collapsed" : "menu-collapsed");
    }

    onMounted(() => {
        onMenuCollapse(localStorage.getItem("menuCollapsed") === "true")
    });
</script>