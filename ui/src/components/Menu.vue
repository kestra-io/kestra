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
import Walk from "vue-material-design-icons/Walk";
import Settings from "vue-material-design-icons/Settings";
Vue.component("walk", Walk);
Vue.component("settings", Settings);
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
                    title: this.$t("flows"),
                    icon: {
                        element: "walk",
                        class: "menu-icon"
                    }
                },
                {
                    href: "/settings",
                    title: this.$t("settings"),
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
/deep/ .menu-icon {
    font-size: 1.5em;
    background-color: #2a2a2e !important;
    padding-bottom: 15px;
    svg {
        top: 3px;
        left: 3px;
    }
}
</style>