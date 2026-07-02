<template>
    <KsDrawer
        v-model="drillDownStore.isOpen"
        direction="rtl"
        size="60%"
        :title="$t('drill down preview')"
    >
        <template v-if="preview?.mode === 'logs' && target">
            <LogsWrapper :filters="buildFullQuery(target)" embed :withCharts="false" />
        </template>
        <template v-else-if="tablePreview && target">
            <KsDataTable
                :data="rows"
                :total="total"
                :loading="loading"
                :loadData="loadData"
                @row-dblclick="onRowDblClick"
            >
                <KsTableColumn
                    v-for="column in tablePreview.columns"
                    :key="column.prop"
                    :prop="column.prop"
                    :label="column.label"
                >
                    <template #default="{row}">
                        <KsExecutionStatus v-if="column.type === 'status'" :status="get(row, column.prop)" size="small" />
                        <KsDateAgo v-else-if="column.type === 'date'" :inverted="true" :date="get(row, column.prop)" />
                        <Labels v-else-if="column.type === 'labels'" :labels="get(row, column.prop)" />
                        <template v-else>{{ get(row, column.prop) }}</template>
                    </template>
                </KsTableColumn>
            </KsDataTable>
        </template>

        <template #footer>
            <KsButton type="primary" @click="openFullPage">
                {{ $t("open full page") }}
            </KsButton>
        </template>
    </KsDrawer>
</template>

<script lang="ts" setup>
    import {computed, ref, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import get from "lodash/get"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import Labels from "../layout/Labels.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import {useDrillDownStore} from "../../stores/drillDown"
    import {getDrillDownPreview} from "./composables/drillDownPreview"
    import {buildFullQuery} from "./composables/chartDrillDown"

    const route = useRoute()
    const router = useRouter()
    const drillDownStore = useDrillDownStore()

    const target = computed(() => drillDownStore.target)
    const preview = computed(() => target.value ? getDrillDownPreview(target.value.name) : undefined)
    const tablePreview = computed(() => {
        const currentPreview = preview.value
        return currentPreview?.mode === "table" ? currentPreview : undefined
    })

    const rows = ref<any[]>([])
    const total = ref(0)
    const loading = ref(false)

    const loadData = async ({page, size}: {page: number; size: number}) => {
        const currentTarget = target.value
        const currentPreview = preview.value
        if (!currentTarget || currentPreview?.mode !== "table") return

        loading.value = true
        try {
            const response = await currentPreview.fetch(buildFullQuery(currentTarget, {page, size}))
            rows.value = response.results
            total.value = response.total
        } finally {
            loading.value = false
        }
    }

    // The drawer's content is destroyed on close (KsDrawer's destroyOnClose) so re-opening it
    // for a new target remounts KsDataTable fresh, but if two "table" targets are opened back to
    // back without the drawer closing in between, local state must be reset explicitly.
    watch(target, () => {
        rows.value = []
        total.value = 0
    })

    const onRowDblClick = (row: any) => {
        const currentPreview = preview.value
        if (currentPreview?.mode !== "table") return

        router.push(currentPreview.rowDetail(row, route.params.tenant as string | undefined))
        drillDownStore.close()
    }

    const openFullPage = () => {
        if (!target.value) return

        router.push({
            name: target.value.name,
            params: {tenant: route.params.tenant},
            query: buildFullQuery(target.value, {size: 100, page: 1}),
        })
        drillDownStore.close()
    }
</script>
