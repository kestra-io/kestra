import {computed} from "vue"
import {useI18n} from "vue-i18n"
import {useRoute} from "vue-router"
import ServerNetworkOutline from "vue-material-design-icons/ServerNetworkOutline.vue"

import type {RouteTab} from "../stores/routeTabs"

/** Tabs for the OSS Admin item. */
export function useAdminTabs() {
    const {t} = useI18n()
    const route = useRoute()

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
            name: "product-tour",
            title: t("product_tour"),
            excludeFromScope: true,
            route: {
                name: "flows/create",
                query: {onboarding: "guided", reset: "true"},
                params: {tenant: route.params.tenant},
            },
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
