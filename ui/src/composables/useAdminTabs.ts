import {computed} from "vue"
import {useI18n} from "vue-i18n"
import ServerNetworkOutline from "vue-material-design-icons/ServerNetworkOutline.vue"

import type {RouteTab} from "../stores/routeTabs"

/** Tabs for the OSS Admin item. */
export function useAdminTabs() {
    const {t} = useI18n()

    const adminTabs = computed<RouteTab[]>(() => [
        {
            title: t("main_configuration"),
            header: true,
        },
        {
            name: "preferences",
            title: t("admin_preferences"),
            route: {name: "preferences"},
        },
        {
            title: t("instance"),
            header: true,
        },
        {
            name: "instance-ee",
            title: t("instance"),
            icon: ServerNetworkOutline,
            locked: true,
            route: {name: "admin/instance"},
        },
    ])

    return {adminTabs}
}
