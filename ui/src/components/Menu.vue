<template>
    <sidebar-menu :menu="menu" @toggle-collapse="onToggleCollapse" :collapsed="collapsed">
        <span slot="toggle-icon">
            <chevron-right v-if="collapsed"/>
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
import Settings from "vue-material-design-icons/Settings";
import TimelineClock from "vue-material-design-icons/TimelineClock";
Vue.component("graph", Graph);
Vue.component("settings", Settings);
Vue.component("timelineclock", TimelineClock);
export default {
    components: {
        ChevronLeft,
        ChevronRight,
        SidebarMenu,
    },
    methods: {
        onToggleCollapse(folded) {
            this.collapsed = folded;
        }
    },
    data() {
        return {
            collapsed: true,
        };
    },
    computed: {
        menu () {
        return [
                {
                    header: true,
                    title: "Menu",
                    hiddenOnCollapse: true
                },
                {
                    href: "/flows",
                    title: this.$t("flows").capitalize(),
                    icon: {
                        element: "graph",
                        class: "menu-icon"
                    }
                },
                {
                    href: "/executions",
                    title: this.$t("executions").capitalize(),
                    icon: {
                        element: "timelineclock",
                        class: "menu-icon"
                    }
                },
                {
                    href: "/settings",
                    title: this.$t("settings").capitalize(),
                    icon: {
                        element: "settings",
                        class: "menu-icon"
                    }
                }
            ]
        }
    }
};
</script>

<style lang="scss" scoped>
@import "../styles/variable";
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