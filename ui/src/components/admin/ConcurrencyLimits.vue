<template>
    <TopNavBar :title="routeInfo.title" />

    <KsDataTable
        ref="dataTable"
        :loadData="loadData"
        :data="data?.results ?? []"
        :total="data?.total ?? 0"
        :currentPage="urlPage"
        :pageSize="urlSize"
        fitHeight
        @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
    >
        <template #empty>
            <Empty v-if="data !== undefined" type="concurrency_limits" />
        </template>
        <KsTableColumn
            v-for="k in KEYS"
            :key="k"
            :prop="k"
            :label="k === 'queued' ? $t('concurrency_limit.queued') : k"
        >
            <template #default="scope">
                <button v-if="k === 'running' && canReset(scope.row as ConcurrencyLimit)" class="edit-running" @click="openDialog(scope.row as ConcurrencyLimit)">
                    {{ scope.row[k] }}
                    <IconEdit />
                </button>
                <span v-else>
                    {{ scope.row[k] }}
                </span>
            </template>
        </KsTableColumn>
    </KsDataTable>
    <KsDialog v-model="editRunning" :title="$t('concurrency_limit.dialog_title')" destroyOnClose :appendToBody="true" :dirty="isEditDirty">
        <KsAlert type="warning" :closable="false">
            {{ $t("concurrency_limit.warning") }}
        </KsAlert>
        <br>
        <KsInputNumber v-model="newRunningCount" />
        <template #footer>
            <KsButton @click="editRunning = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" @click="saveEditRunning()">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script lang="ts" setup>
    import {computed, ref, useTemplateRef, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import TopNavBar from "../layout/TopNavBar.vue"
    import Empty from "../layout/empty/Empty.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useClient} from "@kestra-io/kestra-sdk"
    import IconEdit from "vue-material-design-icons/Pencil.vue"
    import {apiUrlWithTenant} from "override/utils/route"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const authStore = useAuthStore()
    const toast = useToast()

    const baseUrl = computed(() => apiUrlWithTenant(route))

    const routeInfo = computed(() => {
        return {
            title: t("concurrency limits"),
        }
    })

    interface ConcurrencyLimit {
        tenantId: string
        namespace: string,
        flowId: string,
        running: number,
        queued: number
    }

    const KEYS: (keyof ConcurrencyLimit)[] = ["tenantId", "namespace", "flowId", "running", "queued"]

    const axios = useClient()
    const data = ref<{
        total: number;
        results: ConcurrencyLimit[]
    }>()

    const dataTable = useTemplateRef("dataTable")
    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    async function loadData({page, size}: {page: number, size: number}){
        try {
            const response = await axios.get(`${baseUrl.value}/concurrency-limit/search?page=${page}&size=${size}`)
            if(response?.status !== 200){
                throw new Error(`Failed to load concurrency limits: status ${response?.status}`)
            }
            if(page > 1 && response.data.results.length === 0 && response.data.total > 0){
                await router.push({query: {...route.query, page: "1"}})
                return
            }
            data.value = response.data
        } catch (error) {
            data.value = undefined
            throw error
        }
    }

    const editRunning = ref(false)
    const newRunningCount = ref(0)
    const editingRow = ref<ConcurrencyLimit|null>(null)

    const isEditDirty = computed(() => editingRow.value != null && newRunningCount.value !== editingRow.value.running)

    function canReset(row: ConcurrencyLimit) {
        const user = authStore.user
        if (user?.instanceOwner) {
            return true
        }
        return Boolean(row.namespace === ""
            ? user?.isAllowedGlobal(resource.FLOW, action.UPDATE_CONCURRENCY_LIMIT)
            : user?.isAllowed(resource.FLOW, action.UPDATE_CONCURRENCY_LIMIT, row.namespace))
    }

    function openDialog(row: ConcurrencyLimit){
        editRunning.value = true
        newRunningCount.value = row.running
        editingRow.value = row
    }

    async function saveEditRunning(){
        const row = editingRow.value
        if(!row){
            return
        }
        try {
            await axios.put(`${baseUrl.value}/concurrency-limit`, {
                namespace: row.namespace,
                flowId: row.flowId,
                running: newRunningCount.value,
            })
        } catch {
            toast.error(t("concurrency_limit.save_error"))
            return
        }
        editRunning.value = false
        await dataTable.value?.reload()
    }

    watch(baseUrl, () => dataTable.value?.resetAndReload())

    useRouteContext(routeInfo)
</script>

<style scoped>
    .edit-running {
        border: solid 1px transparent;
        background: none;
        display: flex;
        gap: .5rem;
        align-items: center;
        border-radius: 4px;
        &:hover{
            border-color: var(--ks-border-default);
        }
    }
</style>
