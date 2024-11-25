<template>
    <side-bar :generate-menu="generateMenu" @menu-collapse="onCollapse" />
</template>

<script setup>
    import {useLeftMenu} from "override/components/useLeftMenu";
    import SideBar from "../../components/layout/SideBar.vue"

    const $emit = defineEmits(["menu-collapse"])

    function onCollapse(folded) {
        $emit("menu-collapse", folded);
    }

    const {generateMenu} = useLeftMenu();
</script>
