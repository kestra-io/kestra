<template>
    <Navbar :title="routeInfo.title">
        <template #actions>
            <ul>
                <li>
                    <KsButton :icon="Plus" type="primary" @click="openCreate">
                        {{ $t('credential.add') }}
                    </KsButton>
                </li>
            </ul>
        </template>
    </Navbar>

    <section class="full-container">
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :data="credentials"
            :total="total"
            :currentPage="urlPage"
            :pageSize="urlSize"
            :defaultSort="{prop: 'name', order: 'ascending'}"
            :selectable="true"
            :rowKey="(row: CredentialSummary) => `${row.namespace}/${row.name}`"
            :no-data-text="$t('no_results.credentials')"
            fitHeight
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
            @sort-change="({prop, order}: {prop: string | null; order: string | null}) => router.push({query: {...route.query, sort: `${prop}:${order === 'ascending' ? 'asc' : 'desc'}`}})"
            @selection-change="(rows: CredentialSummary[]) => selection = rows"
        >
            <template #empty>
                <Empty type="credentials" />
            </template>

            <template #bulk-actions>
                <button
                    class="bulk-delete-btn"
                    :disabled="!selection.length || bulkDeleting"
                    data-testid="credentials-bulk-delete"
                    @click="removeSelected"
                >
                    <Delete />
                    <span>{{ $t('delete') }}</span>
                </button>
            </template>

            <KsTableColumn prop="namespace" :label="$t('namespace')" sortable="custom" :sortOrders="['ascending', 'descending']">
                <template #default="scope">
                    <KsEntityLink
                        v-if="scope.row?.namespace"
                        entity="namespace"
                        :value="scope.row.namespace"
                        :to="{name: 'namespaces/update', params: {id: scope.row.namespace}}"
                    />
                </template>
            </KsTableColumn>

            <KsTableColumn prop="name" :label="$t('credential.name')" sortable="custom" :sortOrders="['ascending', 'descending']">
                <template #default="scope">
                    <KsId v-if="scope.row?.name !== undefined" :value="scope.row.name" :shrink="false" />
                </template>
            </KsTableColumn>

            <KsTableColumn prop="type" :label="$t('credential.type')">
                <template #default="scope">
                    <KsTag v-if="scope.row?.type" size="small">
                        {{ typeLabel(scope.row.type) }}
                    </KsTag>
                </template>
            </KsTableColumn>

            <KsTableColumn prop="description" :label="$t('credential.description')">
                <template #default="scope">
                    {{ scope.row?.description }}
                </template>
            </KsTableColumn>

            <KsTableColumn prop="updated" :label="$t('credential.updated')">
                <template #default="scope">
                    <KsDateAgo v-if="scope.row?.updated" :inverted="true" :date="scope.row.updated" />
                </template>
            </KsTableColumn>

            <KsTableColumn columnKey="update" className="row-action">
                <template #default="scope">
                    <KsIconButton
                        v-if="scope.row?.name"
                        :tooltip="$t('update')"
                        placement="left"
                        @click="openEdit(scope.row)"
                    >
                        <FileDocumentEdit />
                    </KsIconButton>
                </template>
            </KsTableColumn>

            <KsTableColumn columnKey="delete" className="row-action">
                <template #default="scope">
                    <KsIconButton
                        v-if="scope.row?.name"
                        :tooltip="$t('delete')"
                        placement="left"
                        @click="remove(scope.row)"
                    >
                        <Delete />
                    </KsIconButton>
                </template>
            </KsTableColumn>
        </KsDataTable>

        <CredentialFormDrawer
            v-model="drawerVisible"
            :editing="editing"
            @saved="dataTable?.reload()"
        />
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, useTemplateRef} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"

    import Plus from "vue-material-design-icons/Plus.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue"

    import {KsId, KsIconButton} from "@kestra-io/design-system"
    import Navbar from "../layout/TopNavBar.vue"
    import Empty from "../layout/empty/Empty.vue"
    import CredentialFormDrawer from "./CredentialFormDrawer.vue"

    import useRouteContext from "../../composables/useRouteContext"
    import useRestoreUrl from "../../composables/useRestoreUrl"
    import {useToast} from "../../utils/toast"
    import {useCredentialsStore, type CredentialSummary} from "override/stores/credentials"

    useRestoreUrl()

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()
    const credentialsStore = useCredentialsStore()

    const routeInfo = computed(() => ({title: t("credential.names")}))
    useRouteContext(routeInfo)

    const dataTable = useTemplateRef("dataTable")

    const credentials = ref<CredentialSummary[]>()
    const total = ref(0)
    const selection = ref<CredentialSummary[]>([])
    const bulkDeleting = ref(false)

    const drawerVisible = ref(false)
    const editing = ref<CredentialSummary>()

    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    /** The backend supplies the labels, so the table does not keep its own copy of them. */
    const typeLabel = (type: string) =>
        credentialsStore.types.find(option => option.type === type)?.label ?? type

    onMounted(() => credentialsStore.loadTypes())

    const loadData = async ({page, size, sort}: {page: number; size: number; sort?: string}) => {
        const response = await credentialsStore.search({
            page,
            size,
            sort: sort ?? String(route.query.sort ?? "name:asc"),
        })

        credentials.value = response.results ?? []
        total.value = response.total ?? 0
    }

    const openCreate = () => {
        editing.value = undefined
        drawerVisible.value = true
    }

    const openEdit = (credential: CredentialSummary) => {
        editing.value = credential
        drawerVisible.value = true
    }

    const remove = (credential: CredentialSummary) => {
        toast.confirm(t("delete confirm", {name: credential.name}), async () => {
            await credentialsStore.remove({namespace: credential.namespace, name: credential.name})
            toast.deleted(credential.name)
            dataTable.value?.reload()
        })
    }

    /**
     * Deletes the selection a namespace at a time, because deletion is scoped to the namespace that
     * owns the credential and a selection may span several.
     */
    const removeSelected = () => {
        const chosen = selection.value
        if (!chosen.length) return

        toast.confirm(t("delete confirm", {name: `${chosen.length} ${t("credential.names")}`}), async () => {
            bulkDeleting.value = true
            try {
                const byNamespace = chosen.reduce((acc, credential) => {
                    (acc[credential.namespace] ??= []).push(credential.name)
                    return acc
                }, {} as Record<string, string[]>)

                await Promise.all(
                    Object.entries(byNamespace).map(([namespace, names]) =>
                        credentialsStore.bulkRemove({namespace, names}),
                    ),
                )

                selection.value = []
                toast.deleted(chosen.map(credential => credential.name).join(", "))
                dataTable.value?.reload()
            } finally {
                bulkDeleting.value = false
            }
        })
    }
</script>

<style scoped lang="scss">
    /*
     * Matches the secrets table's bulk button, which in turn follows the 1.x fork's: colours go
     * through theme tokens with the fork's hex as the fallback, so light mode looks like the fork
     * and dark mode still reads.
     */
    .bulk-delete-btn {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 6px 12px;
        font-size: var(--ks-font-size-sm, 13px);
        border: 1px solid var(--ks-border-error, #fecaca);
        background: var(--ks-background-card, #fff);
        color: var(--ks-content-alert, #dc2626);
        border-radius: 6px;
        cursor: pointer;
        transition: background-color 0.15s, border-color 0.15s;

        :deep(svg) {
            width: 14px;
            height: 14px;
        }

        &:hover:not(:disabled) {
            background: var(--ks-background-alert, #fef2f2);
            border-color: var(--ks-content-alert, #dc2626);
        }

        &:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }
    }
</style>
