<template>
    <TopNavBar :title="routeInfo.title" />
    
    <section class="container">
        <DataTable
            striped
            :total="data?.total ?? 0"
        >
            <template #table>
                <NoData v-if="data?.results === undefined || data?.results.length === 0" />
                <el-table
                    v-else
                    :data="data?.results"
                >
                    <el-table-column 
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
                    </el-table-column>
                </el-table>
            </template>
        </DataTable>
        <el-dialog v-model="editRunning" :title="$t('concurrency_limit.dialog_title')" destroyOnClose :appendToBody="true" width="400px">
            <el-alert type="warning" :closable="false" showIcon>
                {{ $t("concurrency_limit.warning") }}
            </el-alert>
            <br>
            <el-input-number v-model="newRunningCount" />
            <template #footer>
                <el-button @click="editRunning = false">
                    {{ $t("cancel") }}
                </el-button>
                <el-button type="primary" @click="saveEditRunning()">
                    {{ $t("save") }}
                </el-button>
            </template>
        </el-dialog>
    </section>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import TopNavBar from "../layout/TopNavBar.vue";
    import useRouteContext from "../../composables/useRouteContext";
    import {useAxios} from "../../utils/axios";
    import IconEdit from "vue-material-design-icons/Pencil.vue";
    import {apiUrl, apiUrlWithoutTenants} from "override/utils/route";
    import DataTable from "../layout/DataTable.vue";
    import NoData from "../layout/NoData.vue";

    const {t} = useI18n();

    const routeInfo = computed(() => {
        return {
            title: t("concurrency limits"),
        };
    });

    interface ConcurrencyLimit {
        tenantId: string
        namespace: string,
        flowId: string,
        running: number
    }

    const KEYS: (keyof ConcurrencyLimit)[] = ["tenantId", "namespace", "flowId", "running"];

    const axios = useAxios();
    const data = ref<{ 
        total: number; 
        results: ConcurrencyLimit[] 
    }>();

    async function loadData(){
        const response = await axios.get(`${apiUrl()}/concurrency-limit/search`);
        if(response?.status !== 200){
            throw new Error(`Failed to load concurrency limits: ${response?.statusText}`);
        }
        data.value = response.data;
    }

    const editRunning = ref(false);
    const newRunningCount = ref(0);
    const editingRow = ref<ConcurrencyLimit|null>(null);

    function openDialog(row: ConcurrencyLimit){
        editRunning.value = true;
        newRunningCount.value = row.running;
        editingRow.value = row;
    }

    async function saveEditRunning(){
        if(editingRow.value){
            editingRow.value.running = newRunningCount.value;
            await axios.put(`${apiUrlWithoutTenants()}/${editingRow.value.tenantId}/concurrency-limit/${editingRow.value.namespace}/${editingRow.value.flowId}`, editingRow.value);
        }
        editRunning.value = false;
    }

    onMounted(() => {
        loadData();
    });

    useRouteContext(routeInfo);
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
            border-color: var(--ks-border-primary);
        }
    }
</style>
