<template>
    <side-bar :generate-menu="generateMenu" />
</template>

<script setup>
    import {useLeftMenu} from "override/components/useLeftMenu";
    import SideBar from "../../components/layout/SideBar.vue"

    const {generateMenu} = useLeftMenu();
</script>
