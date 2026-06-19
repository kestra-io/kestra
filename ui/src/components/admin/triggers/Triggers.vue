<template>
    <TopNavBar :title="$t('triggers')">
        <template v-if="isManageTab" #actions>
            <ul>
                <li>
                    <KsButton :icon="Download" @click="exportTriggers">
                        {{ $t("export_csv") }}
                    </KsButton>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <Tabs :tabs="tabs" routeName="admin/triggers" />
</template>

<script setup lang="ts">
    import {computed, markRaw, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import Download from "vue-material-design-icons/Download.vue"

    import TopNavBar from "../../layout/TopNavBar.vue"
    import Tabs from "../../Tabs.vue"
    import TriggersGrid from "./TriggersGrid.vue"
    import TriggersManage from "./TriggersManage.vue"

    import useRouteContext from "../../../composables/useRouteContext"
    import {useTriggerStore} from "../../../stores/trigger"

    const VALID_TABS = ["add", "manage"] as const

    type ValidTab = typeof VALID_TABS[number];

    const storedDefaultTab = localStorage.getItem("triggersDefaultTab") ?? ""
    const DEFAULT_TAB: ValidTab = VALID_TABS.includes(storedDefaultTab as ValidTab) ? storedDefaultTab as ValidTab : "add"

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const triggerStore = useTriggerStore()

    useRouteContext(computed(() => ({title: t("triggers")})))

    const tabs = computed(() => [
        {name: "add", title: t("triggers_tabs_add"), component: markRaw(TriggersGrid)},
        {name: "manage", title: t("triggers_tabs_manage"), component: markRaw(TriggersManage)},
    ])

    const isManageTab = computed(() => route.params.tab === "manage")

    watch(() => route.params.tab, (tab) => {
        const resolved = VALID_TABS.includes(tab as ValidTab) ? tab as ValidTab : DEFAULT_TAB
        if (tab !== resolved) {
            router.replace({name: "admin/triggers", params: {...route.params, tab: resolved}})
        }
    }, {immediate: true})

    const exportTriggers = () => triggerStore.exportTriggersAsCSV(route.query)
</script>
