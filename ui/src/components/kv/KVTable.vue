<template>
    <KsDataTable
        ref="dataTable"
        :loadData="loadData"
        :data="kvs"
        :total="total"
        :defaultSort="{prop: 'key', order: 'ascending'}"
        @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
        @sort-change="({prop, order}: {column: any; prop: string; order: string | null}) => router.push({query: {...route.query, sort: `${prop}:${order === 'ascending' ? 'asc' : 'desc'}`}})"
        :no-data-text="$t('no_results.kv_pairs')"
        class="fill-height"
        :showSelection="!paneView"
        :rowKey="(row: any) => `${row.namespace}-${row.key}`"
    >
        <template #top>
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
            />
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
                    :tooltip="$t('copy_to_clipboard')"
                    placement="left"
                    @click="Utils.copy(`\{\{ kv('${scope.row.key}') \}\}`)"
                >
                    <ContentCopy />
                </KsIconButton>
            </template>
        </KsTableColumn>

        <KsTableColumn v-if="!paneView" columnKey="update" className="row-action">
            <template #default="scope">
                <KsIconButton
                    v-if="canUpdate(scope.row)"
                    :tooltip="$t('update')"
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
    >
        <KsForm class="ks-horizontal" :model="kv" :rules="rules" ref="formRef">
            <KsFormItem v-if="namespace === undefined" :label="$t('namespace')" prop="namespace" required>
                <NamespaceSelect
                    v-model="kv.namespace"
                    :readOnly="kv.update"
                    :includeSystemNamespace="true"
                    all
                />
            </KsFormItem>

            <KsFormItem :label="$t('key')" prop="key" required>
                <KsInput v-model="kv.key" :disabled="kv.update" />
            </KsFormItem>

            <KsFormItem :label="$t('kv.type')" prop="type" required>
                <KsSelect
                    v-model="kv.type"
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

            <KsFormItem :label="$t('value')" prop="value" :required="kv.type !== 'BOOLEAN'">
                <KsInput v-if="kv.type === 'STRING'" type="textarea" :rows="5" v-model="kv.value" />
                <KsInput v-else-if="kv.type === 'NUMBER'" type="number" v-model="kv.value" />
                <KsSwitch
                    v-else-if="kv.type === 'BOOLEAN'"
                    :activeText="$t('true')"
                    v-model="kv.value"
                    class="switch-text"
                    :activeActionIcon="Check"
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
                <Editor
                    :fullHeight="false"
                    :input="true"
                    :navbar="false"
                    v-else-if="kv.type === 'JSON'"
                    lang="json"
                    v-model="kv.value"
                />
            </KsFormItem>

            <KsFormItem :label="$t('description')" prop="description">
                <KsInput v-model="kv.description" />
            </KsFormItem>

            <KsFormItem :label="$t('expiration')" prop="ttl">
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
            </KsFormItem>
        </KsForm>

        <template #footer>
            <KsButton :icon="ContentSave" @click="saveKv(formRef)" type="primary">
                {{ $t('save') }}
            </KsButton>
        </template>
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
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import _groupBy from "lodash/groupBy";
    import {computed, nextTick, ref, useTemplateRef, watch} from "vue";

    import Check from "vue-material-design-icons/Check.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";

    import {KsId, KsIconButton} from "@kestra-io/design-system";
    import Editor from "../inputs/Editor.vue";
    import InheritedKVs from "./InheritedKVs.vue";

    import {KsFilter as KSFilter} from "@kestra-io/design-system";
    import TimeSelect from "../executions/date-select/TimeSelect.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import useRestoreUrl from "../../composables/useRestoreUrl";

    const {loadInit} = useRestoreUrl();

    import action from "../../models/action";
    import resource from "../../models/resource";

    import Utils from "../../utils/utils";
    import {useToast} from "../../utils/toast";
    import {storageKeys} from "../../utils/constants";
    import {useKvFilter} from "../filter/configurations";
    import moment from "moment-timezone";

    import {useTableColumns} from "../../composables/useTableColumns";

    import {useAuthStore} from "override/stores/auth";
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useKvStore} from "../../stores/kvs.ts";

    import _merge from "lodash/merge";
    const dataTable = useTemplateRef("dataTable");
    const router = useRouter();
    const route = useRoute();
    const toast = useToast();

    const props = withDefaults(defineProps<{
        namespace?: string;
        paneView?: boolean;
        includeInherited?: boolean;
    }>(), {
        namespace: undefined,
        paneView: false,
        includeInherited: false
    });

    const kvFilter = useKvFilter();

    const authStore = useAuthStore();
    const namespacesStore = useNamespacesStore();
    const kvStore = useKvStore();

    const loadData = async ({page, size, sort}: {page: number; size: number; sort?: string}) => {
        if (!loadInit.value) return;
        const kvsResponse = await kvStore.find(loadQuery({
            size,
            page,
            sort: sort ?? String(route.query.sort ?? "name:asc"),
            ...(props.namespace === undefined ? {} : {
                filters: {
                    namespace: {
                        EQUALS: props.namespace
                    }
                }
            })
        }));

        let allKvs = kvsResponse.results ?? [];

        if (props.includeInherited && props.namespace) {
            const parentNamespaces = Utils.getParentNamespaces(props.namespace).slice(0, -1);

            for (const parentNs of parentNamespaces) {
                const parentKvsResponse = await kvStore.find(loadQuery({
                    filters: {
                        namespace: {
                            EQUALS: parentNs
                        }
                    }
                }));

                const parentKvs = parentKvsResponse?.results ?? [];
                if (parentKvs.length > 0) {
                    const currentKeys = new Set(allKvs.map((kv: any) => kv?.key).filter(Boolean));
                    const newKvs = parentKvs.filter(
                        (kv: any) => kv?.key && !currentKeys.has(kv.key)
                    );
                    allKvs.push(...newKvs);
                }
            }
        }

        kvs.value = allKvs;
        total.value = kvsResponse.total ?? 0;
    }

    const loadQuery = (base: any) => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query;
        return _merge(base, filters);
    };

    const filterQuery = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query;
        return filters;
    });

    watch(filterQuery, () => {
        dataTable.value?.resetAndReload();
    }, {deep: true});

    interface KvItem {
        namespace?: string;
        key?: string;
        type: string;
        value?: any;
        ttl?: string;
        update?: boolean;
        description?: string;
    }

    const kv = ref<KvItem>({
        namespace: props.namespace,
        key: undefined,
        type: "STRING",
        value: undefined,
        ttl: undefined,
        update: undefined,
        description: undefined
    });

    const {t} = useI18n();

    const kvs = ref<any[] | undefined>(undefined);

    const storageKey = storageKeys.DISPLAY_KV_COLUMNS;

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
                description: t("filter.table_column.kv.namespace")
            },
            {
                label: t("key"),
                prop: "key",
                default: true,
                description: t("filter.table_column.kv.key")
            },
            {
                label: t("description"),
                prop: "description",
                default: true,
                description: t("filter.table_column.kv.description")
            },
            {
                label: t("last modified"),
                prop: "updateDate",
                default: true,
                description: t("filter.table_column.kv.last modified")
            },
            {
                label: t("expiration date"),
                prop: "expirationDate",
                default: true,
                description: t("filter.table_column.kv.expiration date")
            }
        ];

        return columns.filter(col => {
            if (props.paneView && (col.prop === "namespace" || col.prop === "description" || col.prop === "expirationDate")) {
                return false;
            }
            return true;
        });
    });

    const {visibleColumns, orderedVisibleColumns, updateVisibleColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey: storageKey
    });

    const selection = computed(() => dataTable.value?.selection ?? []);
    // queryBulkAction: reserved for future bulk action support
    // const queryBulkAction = computed(() => dataTable.value?.queryBulkAction ?? false);
    const toggleAllUnselected = () => dataTable.value?.toggleAllUnselected();


    const kvModalTitle = computed(() => {
        return kv.value.key ? "kv.update" : "kv.add";
    });

    const addKvDrawerVisible = computed({
        get() {
            return namespacesStore.addKvModalVisible;
        },
        set(newValue: boolean) {
            namespacesStore.addKvModalVisible = newValue;
        }
    });

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
                        durationValidator(rule, value, callback);
                    } else if (kv.value.type === "JSON") {
                        jsonValidator(rule, value, callback);
                    } else {
                        callback();
                    }
                },
                trigger: "change"
            }
        ],
        ttl: [
            {validator: durationValidator, trigger: "change"}
        ]
    });

    function canUpdate(kvItem: {namespace: string}) {
        return kvItem.namespace !== undefined && authStore.user?.isAllowed(resource.KVSTORE, action.UPDATE, kvItem.namespace);
    }

    function canDelete(kvItem: {namespace: string}) {
        return kvItem.namespace !== undefined && authStore.user?.isAllowed(resource.KVSTORE, action.DELETE, kvItem.namespace);
    }

    function jsonValidator(_rule: any, value: string, callback: (error?: Error) => void) {
        try {
            const parsed = JSON.parse(value);
            if (typeof parsed !== "object" || parsed === null) {
                callback(new Error("Invalid input: Expected a JSON object or array"));
            } else {
                callback();
            }
        } catch {
            callback(new Error("Invalid input: Expected a JSON formatted string"));
        }
    }

    function durationValidator(_rule: any, value: string, callback: (error?: Error) => void) {
        if (value !== undefined && !value.match(/^P(?=[^T]|T.)(?:\d*D)?(?:T(?=.)(?:\d*H)?(?:\d*M)?(?:\d*S)?)?$/)) {
            callback(new Error("datepicker.error"));
        } else {
            callback();
        }
    }

    const total = ref(0);

    function kvKeyDuplicate(_rule: any, value: string, callback: (error?: Error) => void) {
        if (kv.value.update === undefined && kvs.value && kvs.value.find(r => r.namespace === kv.value.namespace && r.key === value)) {
            return callback(new Error("kv.duplicate"));
        } else {
            callback();
        }
    }

    async function updateKvModal(entry: any) {
        kv.value.namespace = entry.namespace;
        kv.value.key = entry.key;
        const {type, value} = await namespacesStore.kv({namespace: entry.namespace, key: entry.key});
        kv.value.type = type;
        // Force the type reset before setting the value
        await nextTick();
        if (type === "JSON") {
            kv.value.value = JSON.stringify(value);
        } else if (type === "BOOLEAN") {
            kv.value.value = value;
        } else if (type === "DATETIME") {
            // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
            // Convert the datetime value to the user's timezone for proper display in the date picker
            const userTimezone = localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) || moment.tz.guess();
            kv.value.value = moment(value).tz(userTimezone).toDate();
        } else {
            kv.value.value = value.toString();
        }
        kv.value.update = true;
        kv.value.description = entry.description;
        addKvDrawerVisible.value = true;
    }

    function removeKv(namespace: string, key: string) {
        toast.confirm(t("delete confirm"), async () => {
            return namespacesStore
                .deleteKv({namespace, key: key})
                .then(() => {
                    toast.deleted(key);
                    dataTable.value?.reload();
                });
        });
    }

    function removeKvs() {
        const groupedByNamespace = _groupBy(selection.value, "namespace");
        const withDeletePermissionGroupedKvs = Object.fromEntries(Object.entries(groupedByNamespace).filter(([namespace]) => authStore.user?.isAllowed(resource.KVSTORE, action.DELETE, namespace)));
        const withDeletePermissionNamespaces = Object.keys(withDeletePermissionGroupedKvs);
        const withoutDeletePermissionNamespaces = Object.keys(groupedByNamespace).filter(n => !withDeletePermissionNamespaces.includes(n));
        toast.confirm(
            t("kv.delete multiple.confirm", {name: Object.values(withDeletePermissionGroupedKvs).reduce((count, kvs) => count + kvs.length, 0)}) +
                (withoutDeletePermissionNamespaces.length === 0 ? "" : "\n" + t("kv.delete multiple.warning")),
            async () => {
                Object.entries(withDeletePermissionGroupedKvs).forEach(([namespace, kvs]) => {
                    namespacesStore
                        .deleteKvs({namespace, request: {keys: kvs.map(kv => kv.key)}})
                        .then(() => {
                            toast.deleted(`${kvs.length} KV(s) from ${namespace} namespace`);
                            toggleAllUnselected();
                            dataTable.value?.reload();
                        });
                });
            });
    }

    function saveKv(form: any) {
        form.validate((valid: boolean) => {
            if (!valid) {
                return false;
            }

            const type = kv.value.type;
            let value: any = kv.value.value;

            if (type === "STRING") {
                value = JSON.stringify(value);
            } else if (["DURATION", "JSON"].includes(type)) {
                value = value || "";
            } else if (type === "DATETIME") {
                value = new Date(value!).toISOString();
            } else if (type === "DATE") {
                value = new Date(value!).toISOString().split("T")[0];
            } else {
                value = String(value);
            }

            const contentType =  "text/plain";

            const namespace = kv.value.namespace!;
            const key = kv.value.key!;
            const description = kv.value.description || "";
            const ttl = kv.value.ttl;

            const payload = {
                namespace,
                key,
                value,
                contentType,
                description,
            };

            if (ttl) {
                (payload as any).ttl = ttl;
            }

            return namespacesStore
                .createKv(payload)
                .then(() => {
                    toast.saved(key);
                    addKvDrawerVisible.value = false;
                    dataTable.value?.reload();
                });
        });
    }

    function resetKv() {
        kv.value = {
            namespace: props.namespace,
            type: "STRING"
        };
    }

    function onTtlChange(value: any) {
        kv.value.ttl = value.timeRange;
    }

    watch(addKvDrawerVisible, (newValue) => {
        if (!newValue) {
            resetKv();
        }
    });

    const formRef = ref();

    watch(() => kv.value.type, (newType) => {
        formRef.value?.clearValidate("value");
        if (newType === "BOOLEAN") kv.value.value = false;
    });

    defineExpose({
        updateVisibleColumns
    });
</script>
