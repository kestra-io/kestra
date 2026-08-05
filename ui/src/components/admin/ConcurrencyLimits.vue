<template>
    <TopNavBar :title="routeInfo.title" />

    <Empty v-if="data?.results === undefined || data?.results.length === 0" type="concurrency_limits" />
    <section v-else class="full-container">
        <KsDataTable :total="data?.total ?? 0" fitHeight>
            <template #table>
                <KsTable
                    :data="data?.results"
                    stripe
                >
                    <KsTableColumn prop="scope" :label="$t('concurrency_limit.scope')">
                        <template #default="scope">
                            {{ $t(SCOPE_LABEL_KEYS[scopeOf(scope.row)]) }}
                        </template>
                    </KsTableColumn>
                    <KsTableColumn
                        v-for="k in KEYS"
                        :key="k"
                        :prop="k"
                        :label="$t(COLUMN_LABEL_KEYS[k])"
                    >
                        <template #default="scope">
                            <button v-if="k === 'running'" class="edit-running" @click="openDialog(scope.row)">
                                {{ scope.row[k] }}
                                <IconEdit />
                            </button>
                            <span v-else>
                                {{ scope.row[k] === "" ? "-" : scope.row[k] }}
                            </span>
                        </template>
                    </KsTableColumn>
                    <KsTableColumn columnKey="delete" className="row-action">
                        <template #default="scope">
                            <KsIconButton
                                :tooltip="$t('delete')"
                                placement="left"
                                @click="removeLimit(scope.row)"
                            >
                                <IconDelete />
                            </KsIconButton>
                        </template>
                    </KsTableColumn>
                </KsTable>
            </template>
        </KsDataTable>
        <KsDialog v-model="editRunning" :title="$t('concurrency_limit.dialog_title')" destroyOnClose :appendToBody="true" :beforeClose="beforeEditClose">
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
    </section>
</template>

<script lang="ts" setup>
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import TopNavBar from "../layout/TopNavBar.vue"
    import Empty from "../layout/empty/Empty.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useClient} from "@kestra-io/kestra-sdk"
    import IconEdit from "vue-material-design-icons/Pencil.vue"
    import IconDelete from "vue-material-design-icons/Delete.vue"
    import {apiUrlWithTenant, apiUrlWithoutTenants} from "override/utils/route"
    import {useDiscardGuard} from "../../composables/useDiscardGuard"
    import {useToast} from "../../utils/toast"

    const {t} = useI18n()
    const toast = useToast()
    const route = useRoute()

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
        running: number
    }

    type Scope = "flow" | "namespace" | "tenant"

    const KEYS: (keyof ConcurrencyLimit)[] = ["tenantId", "namespace", "flowId", "running"]

    const COLUMN_LABEL_KEYS: Record<keyof ConcurrencyLimit, string> = {
        tenantId: "tenant.name",
        namespace: "namespace",
        flowId: "flow",
        running: "running",
    }

    const SCOPE_LABEL_KEYS: Record<Scope, string> = {
        flow: "flow",
        namespace: "namespace",
        tenant: "tenant.name",
    }

    function scopeOf(row: ConcurrencyLimit): Scope {
        if (row.flowId !== "") {
            return "flow"
        }
        return row.namespace !== "" ? "namespace" : "tenant"
    }

    function rowName(row: ConcurrencyLimit): string {
        switch (scopeOf(row)) {
        case "flow":
            return `${row.namespace}.${row.flowId}`
        case "namespace":
            return row.namespace
        default:
            return row.tenantId
        }
    }

    const axios = useClient()
    const data = ref<{
        total: number;
        results: ConcurrencyLimit[]
    }>()

    async function loadData(){
        const response = await axios.get<{total: number; results: ConcurrencyLimit[]}>(`${baseUrl.value}/concurrency-limit/search`)
        if(response?.status !== 200){
            throw new Error(`Failed to load concurrency limits: status ${response?.status}`)
        }
        data.value = response.data
    }

    const editRunning = ref(false)
    const newRunningCount = ref(0)
    const editingRow = ref<ConcurrencyLimit|null>(null)

    const {guardedClose} = useDiscardGuard(
        () => editingRow.value != null && newRunningCount.value !== editingRow.value.running,
    )
    const beforeEditClose = (done: () => void) => guardedClose(() => done())

    function openDialog(row: ConcurrencyLimit){
        editRunning.value = true
        newRunningCount.value = row.running
        editingRow.value = row
    }

    async function saveEditRunning(){
        if(editingRow.value){
            editingRow.value.running = newRunningCount.value
            await axios.put<ConcurrencyLimit>(`${apiUrlWithoutTenants()}/${editingRow.value.tenantId}/concurrency-limit`, editingRow.value)
        }
        editRunning.value = false
    }

    function removeLimit(row: ConcurrencyLimit){
        toast.confirm(t("delete confirm", {name: rowName(row)}), async () => {
            await axios.delete<void>(`${apiUrlWithoutTenants()}/${row.tenantId}/concurrency-limit`, {params: {namespace: row.namespace, flowId: row.flowId}})
            toast.deleted(rowName(row))
            await loadData()
        })
    }

    watch(baseUrl, () => loadData(), {immediate: true})

    useRouteContext(routeInfo)
</script>

<style lang="scss" scoped>
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
