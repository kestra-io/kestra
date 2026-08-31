<template>
    <KsDataTable
        ref="dataTable"
        :loadData="loadData"
        :data="kvs"
        :total="total"
        :currentPage="urlPage"
        :pageSize="urlSize"
        :defaultSort="{prop: 'key', order: 'ascending'}"
        @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
        @sort-change="({prop, order}: {column: any; prop: string | null; order: string | null}) => router.push({query: {...route.query, sort: `${prop}:${order === 'ascending' ? 'asc' : 'desc'}`}})"
        :no-data-text="$t('no_results.kv_pairs')"
        :fitHeight="!paneView"
        :showSelection="!paneView"
        :rowKey="(row: any) => `${row.namespace}-${row.key}`"
    >
        <template #top v-if="!paneView">
            <KSFilter
                :configuration="kvFilter"
                :tableOptions="{
                    chart: {shown: false},
                    columns: {shown: true},
                    refresh: {shown: true, callback: () => dataTable?.reload()}
                }"
                prefix="kv"
                :buttons="{savedFilters: {shown: !namespace}}"
                :properties="{
                    shown: true,
                    columns: optionalColumns,
                    displayColumns: visibleColumns,
                    storageKey: storageKey
                }"
                @update-properties="updateVisibleColumns"
            />
        </template>

        <template #bulk-actions>
            <KsButton :icon="Delete" type="default" @click="removeKvs()">
                {{ $t("delete") }}
            </KsButton>
        </template>

        <template v-for="colProp in orderedVisibleColumns" :key="colProp">
            <KsTableColumn
                v-if="colProp === 'namespace' && namespace === undefined && !paneView"
                prop="namespace"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('namespace')"
            >
                <template #default="scope">
                    <KsEntityLink
                        v-if="scope.row.namespace"
                        entity="namespace"
                        :value="scope.row.namespace"
                        :to="{name: 'namespaces/update', params: {id: scope.row.namespace}}"
                    />
                </template>
            </KsTableColumn>
            <KsTableColumn
                v-else-if="colProp === 'key'"
                prop="key"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('key')"
            >
                <template #default="scope">
                    <KsId v-if="scope.row.key !== undefined" :value="scope.row.key" :shrink="false" />
                </template>
            </KsTableColumn>
            <KsTableColumn
                v-else-if="colProp === 'description' && !paneView"
                prop="description"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('description')"
            />
            <KsTableColumn
                v-else-if="colProp === 'updateDate'"
                prop="updateDate"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('last modified')"
            >
                <template #default="scope">
                    <KsDateAgo :date="convertToUserTimezone(scope.row.updateDate)" inverted />
                </template>
            </KsTableColumn>
            <KsTableColumn
                v-else-if="colProp === 'expirationDate' && !paneView"
                prop="expirationDate"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('expiration date')"
            >
                <template #default="scope">
                    <KsDateAgo v-if="scope.row.expirationDate" :date="convertToUserTimezone(scope.row.expirationDate)" />
                </template>
            </KsTableColumn>
        </template>

        <KsTableColumn columnKey="copy" className="row-action">
            <template #default="scope">
                <KsIconButton
                    v-if="scope.row.key !== undefined"
                    :tooltip="$t('copy_pebble_expression')"
                    placement="left"
                    @click="copyKey(scope.row.key)"
                >
                    <ContentCopy />
                </KsIconButton>
            </template>
        </KsTableColumn>

        <KsTableColumn v-if="!paneView" columnKey="view" className="row-action">
            <template #default="scope">
                <KsIconButton
                    v-if="!canUpdate(scope.row) && canRead(scope.row)"
                    :tooltip="$t('show')"
                    placement="left"
                    @click="viewKvModal(scope.row)"
                >
                    <Eye />
                </KsIconButton>
            </template>
        </KsTableColumn>

        <KsTableColumn v-if="!paneView" columnKey="update" className="row-action">
            <template #default="scope">
                <KsIconButton
                    v-if="canUpdate(scope.row)"
                    data-test="kv-edit"
                    :tooltip="$t('edit')"
                    placement="left"
                    @click="updateKvModal(scope.row)"
                >
                    <FileDocumentEdit />
                </KsIconButton>
            </template>
        </KsTableColumn>

        <KsTableColumn v-if="!paneView" columnKey="delete" className="row-action">
            <template #default="scope">
                <KsIconButton
                    v-if="canDelete(scope.row)"
                    :tooltip="$t('delete')"
                    placement="left"
                    @click="removeKv(scope.row.namespace, scope.row.key)"
                >
                    <Delete />
                </KsIconButton>
            </template>
        </KsTableColumn>
    </KsDataTable>

    <KsDrawer
        v-if="addKvDrawerVisible"
        v-model="addKvDrawerVisible"
        :title="kvModalTitle"
        :beforeClose="beforeKvClose"
    >
        <KsForm class="ks-horizontal" :model="kv" :rules="rules" ref="formRef">
            <KsFormItem v-if="namespace === undefined" :label="$t('namespace')" prop="namespace" required data-test="kv-namespace">
                <NamespaceSelect
                    v-model="kv.namespace"
                    :readOnly="kv.update"
                    :includeSystemNamespace="true"
                    all
                />
            </KsFormItem>

            <KsFormItem :label="$t('key')" prop="key" required data-test="kv-key">
                <KsInput v-model="kv.key" :disabled="kv.update" />
            </KsFormItem>

            <KsFormItem :label="$t('kv.type')" prop="type" required>
                <KsSelect
                    v-model="kv.type"
                    data-test="kv-type"
                    :disabled="kv.update"
                    @change="kv.value = undefined"
                >
                    <KsOption value="STRING" />
                    <KsOption value="NUMBER" />
                    <KsOption value="BOOLEAN" />
                    <KsOption value="DATETIME" />
                    <KsOption value="DATE" />
                    <KsOption value="DURATION" />
                    <KsOption value="JSON" />
                </KsSelect>
            </KsFormItem>

            <KsFormItem :label="$t('value')" prop="value" :required="kv.type !== 'BOOLEAN'" data-test="kv-value">
                <KsInput v-if="kv.type === 'STRING'" type="textarea" :rows="5" v-model="kv.value" />
                <KsInput v-else-if="kv.type === 'NUMBER'" type="number" v-model="kv.value" />
                <KsSwitch
                    v-else-if="kv.type === 'BOOLEAN'"
                    :activeText="$t('true')"
                    v-model="kv.value"
                    class="switch-text"
                />
                <KsDatePicker
                    v-else-if="kv.type === 'DATETIME'"
                    v-model="kv.value"
                    type="datetime"
                />
                <KsDatePicker
                    v-else-if="kv.type === 'DATE'"
                    v-model="kv.value"
                    type="date"
                />
                <TimeSelect
                    v-else-if="kv.type === 'DURATION'"
                    :fromNow="false"
                    :timeRange="kv.value"
                    clearable
                    allowCustom
                    @update:model-value="kv.value = $event.timeRange"
                />
                <KsEditor
                    v-bind="editorBindings"
                    :options="{fullHeight: false}"
                    :inline="true"
                    :navbar="false"
                    v-else-if="kv.type === 'JSON'"
                    lang="json"
                    v-model="kv.value"
                />
            </KsFormItem>

            <KsFormItem :label="$t('description')" prop="description" data-test="kv-description">
                <KsInput v-model="kv.description" />
            </KsFormItem>

            <KsFormItem :label="$t('expiration')" prop="ttl" data-test="kv-ttl">
                <TimeSelect
                    :fromNow="false"
                    allowInfinite
                    allowCustom
                    :placeholder="kv.ttl ? $t('datepicker.custom') : $t('datepicker.never')"
                    :timeRange="kv.ttl"
                    clearable
                    includeNever
                    @update:model-value="onTtlChange"
                />
                <span v-if="currentExpiration" class="expiration-hint" data-test="kv-expiration-hint">
                    {{ $t("kv.expiration_hint", {date: currentExpiration}) }}
                </span>
            </KsFormItem>
        </KsForm>

        <template #footer>
            <KsButton :icon="ContentSave" data-test="kv-save" @click="saveKv(formRef)" type="primary">
                {{ $t('save') }}
            </KsButton>
        </template>
    </KsDrawer>

    <KsDrawer
        v-if="viewKvDrawerVisible"
        v-model="viewKvDrawerVisible"
        :title="$t('show')"
    >
        <KsForm class="ks-horizontal">
            <KsFormItem v-if="viewKv.namespace" :label="$t('namespace')">
                <KsInput :modelValue="viewKv.namespace" disabled />
            </KsFormItem>
            <KsFormItem :label="$t('key')">
                <KsInput :modelValue="viewKv.key" disabled />
            </KsFormItem>
            <KsFormItem :label="$t('kv.type')">
                <KsInput :modelValue="viewKv.type" disabled />
            </KsFormItem>
            <KsFormItem :label="$t('value')">
                <KsInput type="textarea" :rows="5" :modelValue="viewKv.value" readonly />
            </KsFormItem>
            <KsFormItem v-if="viewKv.description" :label="$t('description')">
                <KsInput :modelValue="viewKv.description" disabled />
            </KsFormItem>
        </KsForm>
    </KsDrawer>

    <KsDrawer
        v-if="namespacesStore.inheritedKVModalVisible"
        v-model="namespacesStore.inheritedKVModalVisible"
        :title="$t('kv.inherited')"
    >
        <InheritedKVs :namespace="namespacesStore?.namespace?.id" />
    </KsDrawer>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import _groupBy from "lodash/groupBy"
    import {computed, nextTick, ref, useTemplateRef, watch} from "vue"

    import Delete from "vue-material-design-icons/Delete.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue"
    import Eye from "vue-material-design-icons/Eye.vue"

    import {KsId, KsIconButton, KsEditor, KsFilter as KSFilter} from "@kestra-io/design-system"
    import {routeQueryToQueryFilters} from "../../utils/queryFilters"
    import {date as formatDate} from "../../utils/filters"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {useDiscardGuard} from "../../composables/useDiscardGuard"
    import InheritedKVs from "./InheritedKVs.vue"
    import {formatKvValueForDisplay, hydrateKvValueForForm, serializeKvValueForSave} from "./kvValue"
    import TimeSelect from "../executions/date-select/TimeSelect.vue"
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue"
    import useRestoreUrl from "../../composables/useRestoreUrl"

    const {loadInit} = useRestoreUrl()

    import action from "../../models/action"
    import resource from "../../models/resource"

    import * as Utils from "../../utils/utils"
    import {useToast} from "../../utils/toast"
    import {storageKeys} from "../../utils/constants"
    import {useKvFilter} from "../filter/configurations"
    import moment from "moment-timezone"

    import {useTableColumns} from "@kestra-io/design-system"

    import {useAuthStore} from "override/stores/auth"
    import {useNamespacesStore} from "override/stores/namespaces"
    import {useApiStore} from "../../stores/api"
    import * as KvAPI from "@kestra-io/kestra-sdk/kv"

    import _merge from "lodash/merge"
    const dataTable = useTemplateRef("dataTable")
    const router = useRouter()
    const route = useRoute()
    const toast = useToast()

    const props = withDefaults(defineProps<{
        namespace?: string;
        paneView?: boolean;
        includeInherited?: boolean;
    }>(), {
        namespace: undefined,
        paneView: false,
        includeInherited: false,
    })

    const kvFilter = useKvFilter()

    const authStore = useAuthStore()
    const namespacesStore = useNamespacesStore()
    const apiStore = useApiStore()

    const editorBindings = useEditorBindings()

    const namespaceFilter = (namespace: string) =>
        [{field: "namespace" as const, operation: "EQUALS" as const, value: namespace}]

    const loadData = async ({page, size, sort}: {page: number; size: number; sort?: string}) => {
        if (!loadInit.value) return
        const activeFilters = routeQueryToQueryFilters(route.query)
        const kvsResponse = await KvAPI.listAllKeys(loadQuery({
            size,
            page,
            sort: sort ?? String(route.query.sort ?? "name:asc"),
            filters: [
                ...activeFilters,
                ...(props.namespace === undefined ? [] : namespaceFilter(props.namespace)),
            ],
        }))

        let allKvs = kvsResponse.results ?? []

        if (props.includeInherited && props.namespace) {
            const parentNamespaces = Utils.getParentNamespaces(props.namespace).slice(0, -1)

            for (const parentNs of parentNamespaces) {
                const parentKvsResponse = await KvAPI.listAllKeys(loadQuery({
                    filters: [...activeFilters, ...namespaceFilter(parentNs)],
                }))

                const parentKvs = parentKvsResponse?.results ?? []
                if (parentKvs.length > 0) {
                    const currentKeys = new Set(allKvs.map((kv: any) => kv?.key).filter(Boolean))
                    const newKvs = parentKvs.filter(
                        (kv: any) => kv?.key && !currentKeys.has(kv.key),
                    )
                    allKvs.push(...newKvs)
                }
            }
        }

        kvs.value = allKvs
        total.value = kvsResponse.total ?? 0
    }

    const loadQuery = (base: any) => {
        const {page: _p, size: _s, sort: _so, ...rest} = route.query
        const nonFilterRest = Object.fromEntries(
            Object.entries(rest).filter(([key]) => !key.startsWith("filters[")),
        )
        return _merge(base, nonFilterRest)
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

    interface KvItem {
        namespace?: string;
        key?: string;
        type: string;
        value?: any;
        ttl?: string;
        update?: boolean;
        description?: string;
        expirationDate?: string;
    }

    const kv = ref<KvItem>({
        namespace: props.namespace,
        key: undefined,
        type: "STRING",
        value: undefined,
        ttl: undefined,
        update: undefined,
        description: undefined,
    })

    const ttlTouched = ref(false)

    const kvBaseline = ref("")
    const {guardedClose: guardKvClose} = useDiscardGuard(() => JSON.stringify(kv.value) !== kvBaseline.value)
    const beforeKvClose = (done: () => void) => guardKvClose(() => done())

    const {t} = useI18n()

    const kvs = ref<any[] | undefined>(undefined)

    const storageKey = storageKeys.DISPLAY_KV_COLUMNS

    const TIMEZONE = localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) || Intl.DateTimeFormat().resolvedOptions().timeZone
    const convertToUserTimezone = (date: string | Date) => {
        return moment.utc(date).tz(TIMEZONE).toDate()
    }

    const optionalColumns = computed(() => {
        const columns = [
            {
                label: t("namespace"),
                prop: "namespace",
                default: true,
                description: t("filter.table_column.kv.namespace"),
            },
            {
                label: t("key"),
                prop: "key",
                default: true,
                description: t("filter.table_column.kv.key"),
            },
            {
                label: t("description"),
                prop: "description",
                default: true,
                description: t("filter.table_column.kv.description"),
            },
            {
                label: t("last modified"),
                prop: "updateDate",
                default: true,
                description: t("filter.table_column.kv.last modified"),
            },
            {
                label: t("expiration date"),
                prop: "expirationDate",
                default: true,
                description: t("filter.table_column.kv.expiration date"),
            },
        ]

        return columns.filter(col => {
            if (props.paneView && (col.prop === "namespace" || col.prop === "description" || col.prop === "expirationDate")) {
                return false
            }
            return true
        })
    })

    const {visibleColumns, orderedVisibleColumns, updateVisibleColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey: storageKey,
    })

    const selection = computed(() => dataTable.value?.selection ?? [])
    // queryBulkAction: reserved for future bulk action support
    // const queryBulkAction = computed(() => dataTable.value?.queryBulkAction ?? false);
    const toggleAllUnselected = () => dataTable.value?.toggleAllUnselected()


    const kvModalTitle = computed(() => {
        return kv.value.key ? t("kv.update", {key: kv.value.key}) : t("kv.add")
    })

    const addKvDrawerVisible = computed({
        get() {
            return namespacesStore.addKvModalVisible
        },
        set(newValue: boolean) {
            namespacesStore.addKvModalVisible = newValue
        },
    })

    const rules = ref({
        key: [
            {required: true, trigger: "change"},
            {validator: kvKeyDuplicate, trigger: "change"},
        ],
        value: [
            {required: true, trigger: "change"},
            {
                validator: (rule: any, value: string, callback: (error?: Error) => void) => {
                    if (kv.value.type === "DURATION") {
                        durationValidator(rule, value, callback)
                    } else if (kv.value.type === "JSON") {
                        jsonValidator(rule, value, callback)
                    } else {
                        callback()
                    }
                },
                trigger: "change",
            },
        ],
        ttl: [
            {validator: durationValidator, trigger: "change"},
        ],
    })

    function canUpdate(kvItem: {namespace: string}) {
        return kvItem.namespace !== undefined && authStore.user?.isAllowed(resource.KVSTORE, action.UPDATE, kvItem.namespace)
    }

    function canDelete(kvItem: {namespace: string}) {
        return kvItem.namespace !== undefined && authStore.user?.isAllowed(resource.KVSTORE, action.DELETE, kvItem.namespace)
    }

    function canRead(kvItem: {namespace: string}) {
        return kvItem.namespace !== undefined && authStore.user?.isAllowed(resource.KVSTORE, action.VIEW, kvItem.namespace)
    }

    function jsonValidator(_rule: any, value: string, callback: (error?: Error) => void) {
        try {
            const parsed = JSON.parse(value)
            if (typeof parsed !== "object" || parsed === null) {
                callback(new Error("Invalid input: Expected a JSON object or array"))
            } else {
                callback()
            }
        } catch {
            callback(new Error("Invalid input: Expected a JSON formatted string"))
        }
    }

    function durationValidator(_rule: any, value: string, callback: (error?: Error) => void) {
        if (value !== undefined && !value.match(/^P(?=[^T]|T.)(?:\d*D)?(?:T(?=.)(?:\d*H)?(?:\d*M)?(?:\d*S)?)?$/)) {
            callback(new Error(t("invalid duration")))
        } else {
            callback()
        }
    }

    const total = ref(0)

    function kvKeyDuplicate(_rule: any, value: string, callback: (error?: Error) => void) {
        if (kv.value.update === undefined && kvs.value && kvs.value.find(r => r.namespace === kv.value.namespace && r.key === value)) {
            return callback(new Error(t("kv.duplicate")))
        } else {
            callback()
        }
    }

    async function updateKvModal(entry: any) {
        kv.value.namespace = entry.namespace
        kv.value.key = entry.key
        const {type, value} = await namespacesStore.kv({namespace: entry.namespace, key: entry.key}) as {type: string, value: any}
        kv.value.type = type
        // Force the type reset before setting the value
        await nextTick()
        kv.value.value = hydrateKvValueForForm(type, value, localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) ?? undefined)
        kv.value.update = true
        kv.value.description = entry.description
        kv.value.expirationDate = entry.expirationDate
        kv.value.ttl = entry.expirationDate ? remainingTtl(entry.expirationDate) : undefined
        ttlTouched.value = false

        addKvDrawerVisible.value = true
    }

    function remainingTtl(expirationDate: string): string | undefined {
        const expiration = moment(expirationDate)
        const now = moment()

        if (!expiration.isValid() || !expiration.isAfter(now)) {
            return undefined
        }

        return moment.duration(Math.round(expiration.diff(now) / 1000) * 1000).toISOString()
    }

    const currentExpiration = computed(() => {
        if (!kv.value.update || !kv.value.expirationDate) {
            return undefined
        }

        const expiration = moment(kv.value.expirationDate)

        return expiration.isValid() && expiration.isAfter(moment()) ? formatDate(kv.value.expirationDate) : undefined
    })

    const viewKvDrawerVisible = ref(false)
    const viewKv = ref<{namespace?: string; key?: string; type?: string; value?: string; description?: string}>({})

    async function viewKvModal(entry: any) {
        const {type, value} = await namespacesStore.kv({namespace: entry.namespace, key: entry.key}) as {type: string, value: any}
        const userTimezone = localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) || moment.tz.guess()
        viewKv.value = {
            namespace: entry.namespace,
            key: entry.key,
            type,
            value: formatKvValueForDisplay(type, value, userTimezone),
            description: entry.description,
        }
        viewKvDrawerVisible.value = true
    }

    async function copyKey(key: string) {
        await Utils.copy(`{{ kv('${key}') }}`)
        toast.success(t("copied"))
    }

    function removeKv(namespace: string, key: string) {
        toast.confirm(t("delete confirm"), async () => {
            return namespacesStore
                .deleteKv({namespace, key: key})
                .then(() => {
                    toast.deleted(key)
                    dataTable.value?.reload()
                })
        })
    }

    function removeKvs() {
        const groupedByNamespace = _groupBy(selection.value, "namespace")
        const withDeletePermissionGroupedKvs = Object.fromEntries(Object.entries(groupedByNamespace).filter(([namespace]) => authStore.user?.isAllowed(resource.KVSTORE, action.DELETE, namespace)))
        const withDeletePermissionNamespaces = Object.keys(withDeletePermissionGroupedKvs)
        const withoutDeletePermissionNamespaces = Object.keys(groupedByNamespace).filter(n => !withDeletePermissionNamespaces.includes(n))
        toast.confirm(
            t("kv.delete multiple.confirm", {name: Object.values(withDeletePermissionGroupedKvs).reduce((count, group) => count + group.length, 0)}) +
                (withoutDeletePermissionNamespaces.length === 0 ? "" : "\n" + t("kv.delete multiple.warning")),
            async () => {
                Object.entries(withDeletePermissionGroupedKvs).forEach(([namespace, group]) => {
                    namespacesStore
                        .deleteKvs({namespace, request: {keys: group.map(item => item.key)}})
                        .then(() => {
                            toast.deleted(`${group.length} KV(s) from ${namespace} namespace`)
                            toggleAllUnselected()
                            dataTable.value?.reload()
                        })
                })
            })
    }

    function saveKv(form: any) {
        form.validate((valid: boolean) => {
            if (!valid) {
                return false
            }

            const type = kv.value.type
            const value = serializeKvValueForSave(type, kv.value.value)

            const contentType =  "text/plain"

            const namespace = kv.value.namespace!
            const key = kv.value.key!
            const description = kv.value.description || ""
            // An untouched TTL is recomputed from the stored expiration at save time, so that
            // saving other fields keeps the expiration instead of shifting it by the drawer-open time.
            const preservedTtl = kv.value.update && !ttlTouched.value && kv.value.expirationDate
                ? remainingTtl(kv.value.expirationDate)
                : undefined
            const ttl = preservedTtl ?? kv.value.ttl

            const payload = {
                namespace,
                key,
                value,
                contentType,
                description,
            }

            if (ttl) {
                (payload as any).ttl = ttl
            }

            // update flag is set by updateKvModal(); setKeyValue() is an upsert and can't tell them apart.
            const wasUpdate = kv.value.update === true

            return namespacesStore
                .createKv(payload)
                .then(() => {
                    apiStore.posthogEvents({
                        type: wasUpdate ? "SECRET_UPDATED" : "SECRET_CREATED",
                        secret_type: "kv",
                        namespace,
                        value_type: type,
                        has_ttl: Boolean(ttl),
                    })

                    toast.saved(key)
                    addKvDrawerVisible.value = false
                    dataTable.value?.reload()
                })
        })
    }

    function resetKv() {
        kv.value = {
            namespace: props.namespace,
            type: "STRING",
        }
    }

    function onTtlChange(value: any) {
        if (value.timeRange !== kv.value.ttl) {
            ttlTouched.value = true
        }
        kv.value.ttl = value.timeRange
    }

    watch(addKvDrawerVisible, (newValue) => {
        if (newValue) {
            nextTick(() => {
                kvBaseline.value = JSON.stringify(kv.value)
            })
        } else {
            resetKv()
        }
    })

    const formRef = ref()

    watch(() => kv.value.type, (newType) => {
        formRef.value?.clearValidate("value")
        if (newType === "BOOLEAN") kv.value.value = false
    })

    defineExpose({
        updateVisibleColumns,
    })
</script>

<style lang="scss" scoped>
    .expiration-hint {
        display: block;
        margin-top: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }
</style>
