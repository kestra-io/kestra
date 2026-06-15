<template>
    <TopNavBar :title="routeInfo.title" />

    <Empty v-if="data?.results === undefined || data?.results.length === 0" type="concurrency_limits" />
    <section v-else class="container">
        <KsDataTable :total="data?.total ?? 0">
            <template #table>
                <KsTable
                    :data="data?.results"
                    stripe
                >
                    <KsTableColumn
                        v-for="k in KEYS"
                        :key="k"
                        :prop="k"
                        :label="k"
                    >
                        <template #default="scope">
                            <button v-if="k === 'running'" class="edit-running" @click="openDialog(scope.row)">
                                {{ scope.row[k] }}
                                <IconEdit />
                            </button>
                            <span v-else>
                                {{ scope.row[k] }}
                            </span>
                        </template>
                    </KsTableColumn>
                </KsTable>
            </template>
        </KsDataTable>
        <KsDialog v-model="editRunning" :title="$t('concurrency_limit.dialog_title')" destroyOnClose :appendToBody="true" width="400px" :beforeClose="beforeEditClose">
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
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import TopNavBar from "../layout/TopNavBar.vue"
    import Empty from "../layout/empty/Empty.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useClient} from "@kestra-io/kestra-sdk"
    import IconEdit from "vue-material-design-icons/Pencil.vue"
    import {apiUrl, apiUrlWithoutTenants} from "override/utils/route"
    import {useDiscardGuard} from "../../composables/useDiscardGuard"

    const {t} = useI18n()

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

    const KEYS: (keyof ConcurrencyLimit)[] = ["tenantId", "namespace", "flowId", "running"]

    const axios = useClient()
    const data = ref<{ 
        total: number; 
        results: ConcurrencyLimit[] 
    }>()

    async function loadData(){
        const response = await axios.get(`${apiUrl()}/concurrency-limit/search`)
        if(response?.status !== 200){
            throw new Error(`Failed to load concurrency limits: ${response?.statusText}`)
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
            await axios.put(`${apiUrlWithoutTenants()}/${editingRow.value.tenantId}/concurrency-limit/${editingRow.value.namespace}/${editingRow.value.flowId}`, editingRow.value)
        }
        editRunning.value = false
    }

    onMounted(() => {
        loadData()
    })

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
