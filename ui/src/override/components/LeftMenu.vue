<template>
  <SideBar v-if="menu" :menu="menu" :showLink="showLink" @menu-collapse="onCollapse">
    <template #footer>
      <Auth />    
    </template>
  </SideBar>
</template>

<script setup lang="ts">
import { useLeftMenu } from "override/components/useLeftMenu";
import SideBar from "../../components/layout/SideBar.vue";
import Auth from "../../override/components/auth/Auth.vue";

interface Props {
  showLink?: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (event: "menu-collapse", folded: boolean): void;
}>();

function onCollapse(folded: boolean): void {
  emit("menu-collapse", folded);
}

const { menu } = useLeftMenu();
</script>

<style scoped lang="scss">
#side-menu {
  .el-select {
    padding: 0 30px;
    padding-bottom: 15px;
    transition: all 0.2s ease;
    background-color: transparent;
  }
  &.vsm_collapsed {
    .el-select {
      padding-left: 5px;
      padding-right: 5px;
    }
  }
}
</style>
