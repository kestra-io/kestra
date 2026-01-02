import {shallowRef} from "vue";
import {vueRouter} from "storybook-vue3-router";
import HomeIcon from "vue-material-design-icons/Home.vue";
import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
import PlayOutline from "vue-material-design-icons/PlayOutline.vue";
import CogOutline from "vue-material-design-icons/CogOutline.vue";
import ViewDashboardVariantOutline from "vue-material-design-icons/ViewDashboardVariantOutline.vue";
import ChartBoxOutline from "vue-material-design-icons/ChartBoxOutline.vue";
import ShieldCheckOutline from "vue-material-design-icons/ShieldCheckOutline.vue";
import ServerOutline from "vue-material-design-icons/ServerOutline.vue";
import ShieldLockOutline from "vue-material-design-icons/ShieldLockOutline.vue"

import SideBar from "../../../src/components/layout/SideBar.vue";


export default {
  title: "Layout/SideBar",
  component: SideBar,
  decorators: [
    vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"}
        },
          {
            path: "/dashboard",
            name: "dashboard",
            component: {template: "<div>dashboard</div>"}
          },{
            path: "/dashboard/:menu",
            name: "dashboard/menu",
            component: {template: "<div>/dashboard sub</div>"}
          },
          {
            path: "/:graball/:menu?",
            name: "graball",
            component: {template: "<div>/dashboard sub</div>"}
          },
        ])
  ]
};

const Template = (args) => ({
  setup() {
    return () => <SideBar {...args} />;
  },
});

export const Default = Template.bind({});
Default.args = {
  menu: [
    {
      title: "Home",
      href: "/",
      icon: {
        element: shallowRef(HomeIcon),
        class: "menu-icon"
        },
    },
    {
        title: "Flows",
        href: "/flows",
        icon: {
          element: shallowRef(ContentCopy),
          class: "menu-icon"
        },
    },
    {
        title: "Executions",
        href: "/executions",
        icon: {
          element: shallowRef(PlayOutline),
          class: "menu-icon"
        },
    },
    {
      title: "Dashboard",
      href: "/dashboard",
      icon: {
        element: shallowRef(ViewDashboardVariantOutline),
        class: "menu-icon"
      },
      child: [
        {
          title: "Submenu 1",
          href: "/dashboard/submenu1",
          icon: {
            element: shallowRef(ShieldCheckOutline),
            class: "menu-icon"
          },
        },
        {
          title: "Submenu 2",
          href: "/dashboard/submenu2",
          icon: {
            element: shallowRef(ChartBoxOutline),
            class: "menu-icon"
          },
        },
      ],
    },
    {
      title: "Settings",
      href: "/settings",
      icon: {
        element: shallowRef(CogOutline),
        class: "menu-icon"
      },
      child: [
        {
          title: "Submenu 1",
          href: "/settings/submenu1",
          icon: {
            element: shallowRef(ShieldLockOutline),
            class: "menu-icon"
          },
        },
        {
          title: "Submenu 2",
          href: "/settings/submenu2",
          icon: {
            element: shallowRef(ServerOutline),
            class: "menu-icon"
          },
        },
      ]
    },
  ]
};
