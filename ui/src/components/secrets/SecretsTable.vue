<template>
    <div class="secrets-table">
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :data="secrets"
            :total="total"
            :currentPage="urlPage"
            :pageSize="urlSize"
            :defaultSort="{prop: 'key', order: 'ascending'}"
            :selectable="false"
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
            @sort-change="({prop, order}: {column: any; prop: string | null; order: string | null}) => router.push({query: {...route.query, sort: `${prop}:${order === 'ascending' ? 'asc' : 'desc'}`}})"
            :no-data-text="$t('no_results.secrets')"
            :fitHeight="!paneView && !keyOnly"
            :rowKey="(row: any) => `${row.namespace}-${row.key}`"
        >
            <template #top v-if="!paneView">
                <KSFilter
                    :configuration="secretsFilter"
                    :tableOptions="{
                        chart: {shown: false},
                        refresh: {shown: true, callback: () => dataTable?.reload()}
                    }"
                    :prefix="'secrets'"
                    :buttons="{savedFilters: {shown: !namespace}}"
                    :properties="{
                        shown: true,
                        columns: optionalColumns,
                        displayColumns,
                        storageKey: storageKey
                    }"
                    @update-properties="updateDisplayColumns"
                />
            </template>

            <KsTableColumn
                prop="key"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="keyOnly ? $t('secret.names') : $t('key')"
            >
                <template #default="scope">
                    <KsId v-if="scope.row?.key !== undefined" :value="scope.row.key" :shrink="false" />
                </template>
            </KsTableColumn>

            <KsTableColumn
                v-for="col in visibleColumns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
                :sortable="col.prop === 'namespace' ? 'custom' : false"
                :sortOrders="col.prop === 'namespace' ? ['ascending', 'descending'] : []"
            >
                <template #default="scope">
                    <template v-if="col.prop === 'namespace'">
                        <KsEntityLink
                            v-if="scope.row?.namespace"
                            entity="namespace"
                            :value="scope.row.namespace"
                            :to="{name: 'namespaces/update', params: {id: scope.row.namespace}}"
                        />
                    </template>
                    <template v-else-if="col.prop === 'description'">
                        {{ scope.row?.description }}
                    </template>
                    <template v-else-if="col.prop === 'tags'">
                        <Labels v-if="scope.row?.tags !== undefined" :labels="scope.row.tags" readOnly class="no-pointer-events" />
                    </template>
                </template>
            </KsTableColumn>

            <KsTableColumn columnKey="locked" className="row-action">
                <template #default="scope">
                    <KsTooltip
                        v-if="scope.row?.namespace !== undefined && areNamespaceSecretsReadOnly"
                    >
                        <template #content>
                            <span v-html="$t('secret.isReadOnly')" />
                        </template>
                        <KsIcon class="d-flex justify-content-center">
                            <Lock />
                        </KsIcon>
                    </KsTooltip>
                </template>
            </KsTableColumn>

            <KsTableColumn columnKey="copy" className="row-action">
                <template #default="scope">
                    <KsIconButton
                        :tooltip="$t('copy_to_clipboard')"
                        placement="left"
                        @click="Utils.copy(`\{\{ secret('${scope.row?.key}') \}\}`)"
                    >
                        <ContentCopy />
                    </KsIconButton>
                </template>
            </KsTableColumn>

            <KsTableColumn
                v-if="!keyOnly && !paneView"
                columnKey="update"
                className="row-action"
            >
                <template #default="scope">
                    <KsIconButton
                        v-if="canUpdate(scope.row)"
                        :tooltip="$t('update')"
                        placement="left"
                        @click="updateSecretModal(scope.row)"
                    >
                        <FileDocumentEdit />
                    </KsIconButton>
                </template>
            </KsTableColumn>

            <KsTableColumn
                v-if="!keyOnly && !paneView"
                columnKey="delete"
                className="row-action"
            >
                <template #default="scope">
                    <KsIconButton
                        v-if="canDelete(scope.row)"
                        :tooltip="$t('delete')"
                        placement="left"
                        @click="removeSecret(scope.row)"
                    >
                        <Delete />
                    </KsIconButton>
                </template>
            </KsTableColumn>
        </KsDataTable>

        <KsDialog
            v-if="addSecretDrawerVisible"
            v-model="addSecretDrawerVisible"
            :title="secretModalTitle"
            :beforeClose="beforeSecretClose"
            formLayout
        >
            <KsForm labelPosition="left" :model="secret" :rules="rules" ref="form">
                <KsFormItem
                    v-if="namespace === undefined"
                    :label="$t('namespace')"
                    prop="namespace"
                    required
                    inline
                    class="field-item"
                >
                    <NamespaceSelect
                        v-model="secret.namespace"
                        :readOnly="secret.update"
                        :includeSystemNamespace="true"
                        all
                    />
                </KsFormItem>
                <KsFormItem :label="$t('secret.key')" prop="key" required inline class="field-item">
                    <KsInput v-model="secret.key" :disabled="secret.update" :placeholder="$t('secret.keyPlaceholder')" required />
                </KsFormItem>
                <KsFormItem v-if="!secret.update" :label="$t('secret.name')" prop="value" required inline class="field-item">
                    <KsPassword v-model="secret.value" :placeholder="secretModalTitle" />
                </KsFormItem>
                <KsFormItem v-if="secret.update" :label="$t('secret.name')" prop="value" inline class="field-item">
                    <div class="secret-value-control">
                        <KsPassword
                            v-model="secret.value"
                            :placeholder="secretModalTitle"
                            :disabled="!secret.updateValue"
                        />
                        <KsSwitch
                            inlinePrompt
                            v-model="secret.updateValue"
                        />
                    </div>
                </KsFormItem>
                <KsFormItem :label="$t('secret.description')" prop="description" labelPosition="top">
                    <KsInput
                        v-model="secret.description"
                        :placeholder="$t('secret.descriptionPlaceholder')"
                        type="textarea"
                        :rows="2"
                        resize="vertical"
                    />
                </KsFormItem>
                <KsFormItem prop="tags" labelPosition="top" class="secret-tags-item">
                    <template #label>
                        <div class="secret-tags-label">
                            <span>{{ $t('secret.tags') }}</span>
                            <KsButton :icon="Plus" @click="addSecretTag" type="default" size="small">
                                {{ $t('secret.addTag') }}
                            </KsButton>
                        </div>
                    </template>
                    <div class="secret-tag-row" v-for="(tag, index) in secret.tags" :key="index">
                        <KsInput class="tag-key" required v-model="tag.key" :placeholder="$t('key')" />
                        <KsInput class="tag-value" required v-model="tag.value" :placeholder="$t('value')" />
                        <KsButton :icon="Delete" @click="removeSecretTag(index)" />
                    </div>
                </KsFormItem>
            </KsForm>

            <template #footer>
                <KsButton @click="addSecretDrawerVisible = false">
                    {{ $t('cancel') }}
                </KsButton>
                <KsButton :icon="ContentSave" @click="saveSecret(form)" type="primary">
                    {{ $t('save') }}
                </KsButton>
            </template>
        </KsDialog>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import type {FormInstance} from "@kestra-io/design-system"
    import {ref, computed, watch, onMounted, nextTick, useTemplateRef} from "vue"
    import _merge from "lodash/merge"

    import Lock from "vue-material-design-icons/Lock.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue"

    import {KsId, KsIconButton, KsPassword} from "@kestra-io/design-system"
    import Labels from "../layout/Labels.vue"
    import {KsFilter as KSFilter, routeQueryToQueryFilters} from "@kestra-io/design-system"
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue"

    import action from "../../models/action"
    import resource from "../../models/resource"
    import * as Utils from "../../utils/utils"
    import {useToast} from "../../utils/toast"
    import {storageKeys} from "../../utils/constants"
    import * as SecretsAPI from "@kestra-io/kestra-sdk/secrets"
    import {useAuthStore} from "override/stores/auth"
    import {useNamespacesStore} from "override/stores/namespaces"
    import {useSecretsFilter} from "../filter/configurations"
    import {useTableColumns} from "../../composables/useTableColumns"
    import {useDiscardGuard} from "../../composables/useDiscardGuard"

    const secretsFilter = useSecretsFilter()

    interface SecretForm {
        value: string;
        namespace?: string;
        key?: string;
        description?: string;
        update?: boolean;
        updateValue?: boolean;
        tags: {key?: string; value?: string}[];
    }

    interface NamespaceSecret {
        key: string;
        namespace?: string;
        description?: string;
        tags?: {key?: string; value?: string}[];
    }

    const props = withDefaults(defineProps<{
        addSecretModalVisible?: boolean;
        namespace?: string;
        filterable?: boolean;
        keyOnly?: boolean;
        paneView?: boolean;
        namespaceColumn?: boolean;
        includeInherited?: boolean;
    }>(), {
        addSecretModalVisible: false,
        namespace: undefined,
        filterable: true,
        keyOnly: false,
        paneView: false,
        namespaceColumn: undefined,
        includeInherited: false,
    })

    const emit = defineEmits<{
        "update:addSecretModalVisible": [value: boolean];
        "update:isSecretReadOnly": [value: boolean];
        hasData: [value: boolean];
    }>()

    const {t} = useI18n()
    const toast = useToast()
    const route = useRoute()
    const router = useRouter()
    const authStore = useAuthStore()
    const namespacesStore = useNamespacesStore()

    const form = ref<FormInstance>()

    const total = ref(0)
    const hasData = ref<boolean>()
    const areNamespaceSecretsReadOnly = ref(false)
    const secrets = ref<(NamespaceSecret & {namespace?: string})[]>()

    const secret = ref<SecretForm>({
        namespace: props.namespace,
        key: undefined,
        value: "",
        description: undefined,
        tags: [{key: undefined, value: undefined}],
        update: undefined,
        updateValue: undefined,
    })

    const secretBaseline = ref("")
    const {guardedClose: guardSecretClose} = useDiscardGuard(() => JSON.stringify(secret.value) !== secretBaseline.value)
    const beforeSecretClose = (done: () => void) => guardSecretClose(() => done())

    const storageKey = storageKeys.DISPLAY_SECRETS_COLUMNS

    const optionalColumns = computed(() => {
        const columns = [
            {
                label: t("namespace"),
                prop: "namespace",
                default: true,
                description: t("filter.table_column.secrets.namespace"),
            },
            {
                label: t("description"),
                prop: "description",
                default: true,
                description: t("filter.table_column.secrets.description"),
            },
            {
                label: t("tags"),
                prop: "tags",
                default: true,
                description: t("filter.table_column.secrets.tags"),
            },
        ]

        return columns.filter(col => {
            if (col.prop === "namespace" && !(props.namespace === undefined || props.namespaceColumn)) return false
            if (col.prop === "description" && props.keyOnly) return false
            if (col.prop === "tags" && (props.keyOnly || props.paneView)) return false
            return true
        })
    })

    const {visibleColumns: displayColumns, updateVisibleColumns: updateDisplayColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey: storageKey,
    })

    const visibleColumns = computed(() =>
        displayColumns.value
            ?.map(prop => optionalColumns.value?.find(c => c.prop === prop))
            ?.filter(Boolean) as any[],
    )

    const secretModalTitle = computed(() => {
        return secret.value?.update
            ? t("secret.update", {name: secret.value?.key})
            : t("secret.add")
    })

    const addSecretDrawerVisible = computed({
        get() {
            return props.addSecretModalVisible
        },
        set(newValue: boolean) {
            emit("update:addSecretModalVisible", newValue)
        },
    })

    const checkSecretValue = (_rule: any, _value: any, callback: any) => {
        if (secret.value?.updateValue && (secret.value.value === undefined || secret.value.value.length === 0)) {
            callback(new Error("Value must not be empty."))
        } else {
            callback()
        }
    }

    const checkSecretTags = (_rule: any, _value: any, callback: any) => {
        const keys = secret.value?.tags?.map((it) => it.key)

        if (secret.value?.tags?.length === 1) {
            if (secret.value?.tags?.[0]?.key === undefined && secret.value?.tags?.[0]?.value === undefined) {
                callback()
                return
            }
        }

        const nullKeys = keys?.filter(item => item === undefined)
        const duplicateKeys = keys?.filter((item, index) => keys.indexOf(item) !== index)

        if (nullKeys?.length > 0) {
            callback(new Error("Tag key must not be empty."))
        } else if (duplicateKeys?.length > 0) {
            callback(new Error("Duplicate tags for keys: " + Array.from(new Set(duplicateKeys))))
        } else {
            callback()
        }
    }

    const rules = {
        key: [
            {required: true, trigger: "change"},
        ],
        value: [
            {
                validator: checkSecretValue,
                trigger: ["blur"],
                required: false,
            },
        ],
        secret: [
            {required: true, trigger: "change"},
        ],
        tags: [
            {
                validator: checkSecretTags,
                trigger: ["blur"],
                required: false,
            },
        ],
    }

    const canUpdate = (item: NamespaceSecret & {namespace?: string}) => {
        return item?.namespace !== undefined &&
            authStore.user?.isAllowed(resource.SECRET, action.UPDATE, item.namespace) &&
            !areNamespaceSecretsReadOnly.value
    }

    const canDelete = (item: NamespaceSecret & {namespace?: string}) => {
        return item?.namespace !== undefined &&
            authStore.user?.isAllowed(resource.SECRET, action.DELETE, item.namespace) &&
            !areNamespaceSecretsReadOnly.value
    }

    const dataTable = useTemplateRef("dataTable")

    const loadQuery = (base: any) => {
        const {page: _p, size: _s, sort: _so, ...rest} = route.query
        const nonFilterRest = Object.fromEntries(
            Object.entries(rest).filter(([key]) => !key.startsWith("filters[")),
        )
        return _merge(base, nonFilterRest)
    }

    const namespaceFilter = (namespace: string) =>
        [{field: "namespace" as const, operation: "EQUALS" as const, value: namespace}]

    const loadData = async ({page, size, sort}: {page: number; size: number; sort?: string}) => {
        const activeFilters = routeQueryToQueryFilters(route.query)
        const secretsResponse = await SecretsAPI.listSecrets(loadQuery({
            size,
            page,
            sort: sort ?? String(route.query.sort ?? "key:asc"),
            filters: [
                ...activeFilters,
                ...(props.namespace === undefined ? [] : namespaceFilter(props.namespace)),
            ],
        }))

        emit("update:isSecretReadOnly", secretsResponse.readOnly ?? false)

        let allSecrets = secretsResponse.results ?? []

        if (props.includeInherited && props.namespace) {
            const parentNamespaces = Utils.getParentNamespaces(props.namespace).slice(0, -1)

            for (const parentNs of parentNamespaces) {
                const parentSecretsResponse = await SecretsAPI.listSecrets(loadQuery({
                    filters: [...activeFilters, ...namespaceFilter(parentNs)],
                }))

                const parentSecrets = parentSecretsResponse?.results ?? []
                if (parentSecrets.length > 0) {
                    const currentKeys = new Set(allSecrets.map((s: any) => s?.key).filter(Boolean))
                    const newSecrets = parentSecrets.filter(
                        (s: any) => s?.key && !currentKeys.has(s.key),
                    )
                    allSecrets.push(...newSecrets)
                }
            }
        }

        hasData.value = (allSecrets.length ?? 0) !== 0
        areNamespaceSecretsReadOnly.value = secretsResponse.readOnly ?? false
        secrets.value = allSecrets
        total.value = secretsResponse.total ?? 0
    }

    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    const filterQueryKey = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query
        return JSON.stringify(filters)
    })

    watch(filterQueryKey, () => {
        dataTable.value?.resetAndReload()
    })

    const updateSecretModal = (secretData: NamespaceSecret) => {
        secret.value.namespace = secretData?.namespace
        secret.value.key = secretData?.key
        secret.value.description = secretData?.description
        secret.value.tags = secretData?.tags?.map((x: any) => ({...x})) ?? [{key: undefined, value: undefined}]
        secret.value.update = true
        secret.value.updateValue = false
        addSecretDrawerVisible.value = true
    }

    const addSecretTag = () => {
        secret.value?.tags?.push({key: "" as any, value: "" as any})
    }

    const removeSecretTag = (index: number) => {
        secret.value?.tags?.splice(index, 1)
    }

    const removeSecret = ({key, namespace}: {key: string; namespace: string}) => {
        toast.confirm(t("delete confirm", {name: key}), () => {
            return namespacesStore
                .deleteSecrets({namespace, key})
                .then(() => {
                    toast.deleted(key)
                })
                .then(() => dataTable.value?.reload())
        })
    }

    const isSecretValueUpdated = () => {
        return !secret.value?.update || secret.value?.updateValue
    }

    const saveSecret = (formRef: FormInstance | undefined) => {
        if (!formRef) return

        formRef.validate((valid: boolean) => {
            if (!valid) {
                return
            }

            const secretData: any = {
                key: secret.value?.key,
                description: secret.value?.description,
                tags: secret.value?.tags
                    ?.map(item => item.value !== undefined ? item : {key: item.key, value: ""})
                    ?.filter(item => item.key !== undefined),
            }

            if (isSecretValueUpdated()) {
                secretData.value = secret.value?.value
            }

            const actionMethod = isSecretValueUpdated()
                ? namespacesStore.createSecrets
                : namespacesStore.patchSecret

            actionMethod({namespace: secret.value?.namespace as string, secret: secretData})
                .then(() => {
                    secret.value!.update = true
                    toast.saved(secret.value?.key || "")
                    addSecretDrawerVisible.value = false
                    resetForm()
                    dataTable.value?.reload()
                })
        })
    }

    const resetForm = () => {
        secret.value = {
            namespace: props.namespace,
            key: undefined,
            value: "",
            description: undefined,
            tags: [{key: undefined, value: undefined}],
            update: undefined,
            updateValue: undefined,
        }
    }

    watch(() => props.addSecretModalVisible, (newValue) => {
        if (newValue) {
            nextTick(() => {
                secretBaseline.value = JSON.stringify(secret.value)
            })
        } else {
            resetForm()
        }
    })

    watch(hasData, (newValue, oldValue) => {
        if (oldValue !== newValue) {
            emit("hasData", newValue!)
        }
    })

    onMounted(() => {
        updateDisplayColumns(
            localStorage.getItem(`columns_${storageKey}`)?.split(",") ||
                optionalColumns.value?.filter(col => col.default).map(col => col.prop),
        )
    })
</script>
<style scoped lang="scss">
    .secrets-table {
        display: flex;
        flex-direction: column;
        min-height: 0;
    }

    .secret-tag-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-2);

        .tag-key {
            flex: 2;
        }

        .tag-value {
            flex: 3;
        }
    }

    .no-pointer-events {
        pointer-events: none;
    }

    .field-item :deep(.kel-form-item__content) {
        flex: 0 0 260px;
        max-width: 260px;
    }

    .field-item :deep(.kel-form-item__content) > * {
        width: 100%;
    }

    .secret-value-control {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-2);
    }

    .secret-value-control > :first-child {
        width: 100%;
    }

    .secret-tags-item :deep(.kel-form-item__label) {
        width: 100%;
    }

    .secret-tags-label {
        display: flex;
        align-items: center;
        justify-content: space-between;
        width: 100%;
    }
</style>
