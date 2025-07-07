<template>
    <left-menu v-if="configs" @menu-collapse="onMenuCollapse" />
    <main>
        <errors v-if="error" :code="error" />
        <slot v-else />
    </main>
    <context-info-bar v-if="configs" />
</template>

<script setup>
    import LeftMenu from "override/components/LeftMenu.vue";
    import Errors from "../../../components/errors/Errors.vue";
    import ContextInfoBar from "../../../components/ContextInfoBar.vue";
    import {useStore} from "vuex";
    import {useCoreStore} from "../../../stores/core";
    import {computed, onMounted} from "vue";

    const store = useStore();
    const coreStore = useCoreStore();
    const configs = computed(() => store.getters["misc/configs"]);
    const error = computed(() => coreStore.error);

    function onMenuCollapse(collapse) {
        document.getElementsByTagName("html")[0].classList.add(!collapse ? "menu-not-collapsed" : "menu-collapsed");
        document.getElementsByTagName("html")[0].classList.remove(collapse ? "menu-not-collapsed" : "menu-collapsed");
    }

    onMounted(() => {
        onMenuCollapse(localStorage.getItem("menuCollapsed") === "true")
    });
</script>