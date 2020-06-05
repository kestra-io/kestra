<template>
  <sidebar-menu
    :menu="menu"
    @toggle-collapse="onToggleCollapse"
    width="180px"
    :collapsed="collapsed"
  >
    <a class="logo" slot="header" href="/">
      <span>
        <img src="../assets/logo-white.svg" alt="Kestra" />
      </span>
    </a>
    <span slot="toggle-icon">
      <chevron-right v-if="collapsed" />
      <chevron-left v-else />
    </span>
  </sidebar-menu>
</template>

<script>
import Vue from "vue";
import { SidebarMenu } from "vue-sidebar-menu";
import ChevronLeft from "vue-material-design-icons/ChevronLeft";
import ChevronRight from "vue-material-design-icons/ChevronRight";
import Graph from "vue-material-design-icons/Graph";
import Cog from "vue-material-design-icons/Cog";
import TimelineClock from "vue-material-design-icons/TimelineClock";
Vue.component("graph", Graph);
Vue.component("settings", Cog);
Vue.component("timelineclock", TimelineClock);
export default {
  components: {
    ChevronLeft,
    ChevronRight,
    SidebarMenu
  },
  methods: {
    onToggleCollapse(folded) {
      this.collapsed = folded;
      localStorage.setItem("menuCollapsed", folded ? "true" : "false");
      this.$emit("onMenuCollapse", folded);
    }
  },
  data() {
    return {
      collapsed: localStorage.getItem("menuCollapsed") === "true"
    };
  },
  computed: {
    menu() {
      return [
        {
          href: "/flows",
          alias: [
            "/flows*"
          ],
          title: this.$t("flows").capitalize(),
          icon: {
            element: "graph",
            class: "menu-icon"
          }
        },
        {
          href: "/executions",
          alias: [
            "/executions*"
          ],
          title: this.$t("executions").capitalize(),
          icon: {
            element: "timelineclock",
            class: "menu-icon"
          }
        },
        {
          href: "/settings",
          alias: [
            "/settings*"
          ],
          title: this.$t("settings").capitalize(),
          icon: {
            element: "settings",
            class: "menu-icon"
          }
        }
      ];
    }
  }
};
</script>

<style lang="scss" scoped>
@import "../styles/variable";

.logo {
  height: 60px;
  border-bottom: 2px solid $secondary;
  overflow: hidden;
  span {
    display: block;
    height: 52px;
    overflow: hidden;
    border-bottom: 2px solid $tertiary;
    img {
      height: 100%;
    }
  }
}

/deep/ .vsm--item {
  transition: opacity 0.2s;
}

/deep/ .menu-icon {
  font-size: 1.5em;
  background-color: $dark !important;
  padding-bottom: 15px;
  svg {
    top: 3px;
    left: 3px;
  }
}
</style>
