<template>
    <KSFilter
        :configuration="kvFilter"
        :tableOptions="{
            chart: {shown: false},
            columns: {shown: true},
            refresh: {shown: true, callback: refresh}
        }"
        :prefix="'kv'"
        :properties="{
            shown: true,
            columns: optionalColumns,
            displayColumns: visibleColumns,
            storageKey: storageKey
        }"
        @update-properties="updateVisibleColumns"
        legacyQuery
    />

    <SelectTable
        :data="filteredKvs"
        ref="selectTable"
        :defaultSort="{prop: 'id', order: 'ascending'}"
        tableLayout="auto"
        fixed
        @selection-change="handleSelectionChange"
        @sort-change="handleSort"
        :infiniteScrollLoad="namespace === undefined ? fetchKvs : undefined"
        :no-data-text="$t('no_results.kv_pairs')"
        class="fill-height"
        :showSelection="!paneView"
    >
        <template #select-actions>
            <BulkSelect
                :selectAll="queryBulkAction"
                :selections="selection"
                @update:select-all="toggleAllSelection"
                @unselect="toggleAllUnselected"
            >
                <el-button :icon="Delete" type="default" @click="removeKvs()">
                    {{ $t("delete") }}
                </el-button>
            </BulkSelect>
        </template>

        <template v-for="colProp in orderedVisibleColumns" :key="colProp">
            <el-table-column
                v-if="colProp === 'namespace' && namespace === undefined && !paneView"
                prop="namespace"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('namespace')"
            />
            <el-table-column
                v-else-if="colProp === 'key'"
                prop="key"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('key')"
            >
                <template #default="scope">
                    <Id v-if="scope.row.key !== undefined" :value="scope.row.key" :shrink="false" />
                </template>
            </el-table-column>
            <el-table-column
                v-else-if="colProp === 'description' && !paneView"
                prop="description"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('description')"
            />
            <el-table-column
                v-else-if="colProp === 'updateDate'"
                prop="updateDate"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('last modified')"
            />
            <el-table-column
                v-else-if="colProp === 'expirationDate' && !paneView"
                prop="expirationDate"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="$t('expiration date')"
            />
        </template>

        <el-table-column columnKey="copy" className="row-action">
            <template #default="scope">
                <el-tooltip v-if="scope.row.key !== undefined" :content="$t('copy_to_clipboard')">
                    <el-button :icon="ContentCopy" link @click="Utils.copy(`\{\{ kv('${scope.row.key}') \}\}`)" />
                </el-tooltip>
            </template>
        </el-table-column>

        <el-table-column v-if="!paneView" columnKey="update" className="row-action">
            <template #default="scope">
                <el-button
                    v-if="canUpdate(scope.row)"
                    :icon="FileDocumentEdit"
                    link
                    @click="updateKvModal(scope.row)"
                />
            </template>
        </el-table-column>

        <el-table-column v-if="!paneView" columnKey="delete" className="row-action">
            <template #default="scope">
                <el-button
                    v-if="canDelete(scope.row)"
                    :icon="Delete"
                    link
                    @click="removeKv(scope.row.namespace, scope.row.key)"
                />
            </template>
        </el-table-column>
    </SelectTable>

    <Drawer
        v-if="addKvDrawerVisible"
        v-model="addKvDrawerVisible"
        :title="kvModalTitle"
    >
        <el-form class="ks-horizontal" :model="kv" :rules="rules" ref="formRef">
            <el-form-item v-if="namespace === undefined" :label="$t('namespace')" prop="namespace" required>
                <NamespaceSelect
                    v-model="kv.namespace"
                    :readonly="kv.update"
                    :includeSystemNamespace="true"
                    all
                />
            </el-form-item>

            <el-form-item :label="$t('key')" prop="key" required>
                <el-input v-model="kv.key" :readonly="kv.update" />
            </el-form-item>

            <el-form-item :label="$t('kv.type')" prop="type" required>
                <el-select
                    v-model="kv.type"
                    @change="kv.value = undefined"
                >
                    <el-option value="STRING" />
                    <el-option value="NUMBER" />
                    <el-option value="BOOLEAN" />
                    <el-option value="DATETIME" />
                    <el-option value="DATE" />
                    <el-option value="DURATION" />
                    <el-option value="JSON" />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('value')" prop="value" :required="kv.type !== 'BOOLEAN'">
                <el-input v-if="kv.type === 'STRING'" type="textarea" :rows="5" v-model="kv.value" />
                <el-input v-else-if="kv.type === 'NUMBER'" type="number" v-model="kv.value" />
                <el-switch
                    v-else-if="kv.type === 'BOOLEAN'"
                    :activeText="$t('true')"
                    v-model="kv.value"
                    class="switch-text"
                    :activeActionIcon="Check"
                />
                <el-date-picker
                    v-else-if="kv.type === 'DATETIME'"
                    v-model="kv.value"
                    type="datetime"
                />
                <el-date-picker
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
            </el-form-item>

            <el-form-item :label="$t('description')" prop="description">
                <el-input v-model="kv.description" />
            </el-form-item>

            <el-form-item :label="$t('expiration')" prop="ttl">
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
            </el-form-item>
        </el-form>

        <template #footer>
            <el-button :icon="ContentSave" @click="saveKv(formRef)" type="primary">
                {{ $t('save') }}
            </el-button>
        </template>
    </Drawer>

    <Drawer
        v-if="namespacesStore.inheritedKVModalVisible"
        v-model="namespacesStore.inheritedKVModalVisible"
        :title="$t('kv.inherited')"
    >
        <InheritedKVs :namespace="namespacesStore?.namespace?.id" />
    </Drawer>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import _groupBy from "lodash/groupBy";
    import {ref, computed, watch, onMounted, useTemplateRef} from "vue";

    import Check from "vue-material-design-icons/Check.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";

    import Id from "../Id.vue";
    import Drawer from "../Drawer.vue";
    import Editor from "../inputs/Editor.vue";
    import InheritedKVs from "./InheritedKVs.vue";
    import BulkSelect from "../layout/BulkSelect.vue";
    //@ts-expect-error No declaration file
    import SelectTable from "../layout/SelectTable.vue";
    import KSFilter from "../filter/components/KSFilter.vue";
    import TimeSelect from "../executions/date-select/TimeSelect.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";

    import action from "../../models/action";
    import permission from "../../models/permission";
    
    import Utils from "../../utils/utils";
    import {useToast} from "../../utils/toast";
    import {storageKeys} from "../../utils/constants";
    import {useKvFilter} from "../filter/configurations";

    import {useTableColumns} from "../../composables/useTableColumns";
    import {useSelectTableActions} from "../../composables/useSelectTableActions";
    import useNamespaces, {NamespaceIterator} from "../../composables/useNamespaces";

    import {useAuthStore} from "override/stores/auth";
    import {useNamespacesStore} from "override/stores/namespaces";

    
    const props = withDefaults(defineProps<{
        namespace?: string;
        paneView?: boolean;
    }>(), {
        namespace: undefined,
        paneView: false
    });

    const route = useRoute();
    const toast = useToast();

    const kvFilter = useKvFilter();

    const authStore = useAuthStore();
    const namespacesStore = useNamespacesStore();

    const selectTable = useTemplateRef<typeof SelectTable>("selectTable");

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
    const namespaceIterator = ref<NamespaceIterator | undefined>(undefined);

    const storageKey = storageKeys.DISPLAY_KV_COLUMNS;

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
                default: false,
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

    const {
        selection, 
        queryBulkAction, 
        handleSelectionChange, 
        toggleAllUnselected, 
        toggleAllSelection} = useSelectTableActions({
            dataTableRef: selectTable
        });

    const searchQuery = computed(() => {
        const q = route.query.q;
        return typeof q === "string" ? q : "";
    });

    const filteredKvs = computed(() => {
        if (!kvs.value) return [];
        if (!searchQuery.value) return kvs.value;
        return kvs.value.filter(kv =>
            kv.key.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
            kv.description?.toLowerCase().includes(searchQuery.value.toLowerCase())
        );
    });

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
        return kvItem.namespace !== undefined && authStore.user?.isAllowed(permission.KVSTORE, action.UPDATE, kvItem.namespace);
    }

    function canDelete(kvItem: {namespace: string}) {
        return kvItem.namespace !== undefined && authStore.user?.isAllowed(permission.KVSTORE, action.DELETE, kvItem.namespace);
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

    async function fetchKvs() {
        let kvFetch;
        if (props.namespace === undefined) {
            if (namespaceIterator.value === undefined) {
                namespaceIterator.value = useNamespaces(20);
            }

            const namespaces = (await namespaceIterator.value.next()).map(n => n.id);
            if (namespaces.length !== 0) {
                const kvsPromises = Promise.all(namespaces.filter(n => authStore.user?.isAllowed(permission.KVSTORE, action.READ, n)).map(async n => {
                    const kvs = await namespacesStore.kvsList({id: n});

                    return kvs.map((kv: any) => {
                        kv.namespace = n;
                        return kv;
                    });
                }));

                kvFetch = (await kvsPromises).flat();
            }
        } else {
            kvFetch = (await namespacesStore.kvsList({id: props.namespace})).map((kv: any) => {
                kv.namespace = props.namespace;
                return kv;
            });
        }

        if (kvFetch === undefined) {
            return undefined;
        }

        kvs.value = kvs.value?.concat(kvFetch) ?? kvFetch;

        if (props.namespace === undefined && filteredKvs.value.length === 0) {
            return fetchKvs();
        }

        return kvFetch;
    }

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
        if (type === "JSON") {
            kv.value.value = JSON.stringify(value);
        } else if (type === "BOOLEAN") {
            kv.value.value = value;
        } else {
            kv.value.value = value.toString();
        }
        kv.value.update = true;
        kv.value.description = entry.description;
        addKvDrawerVisible.value = true;
    }

    function removeKv(namespace: string, key: string) {
        toast.confirm("delete confirm", async () => {
            return namespacesStore
                .deleteKv({namespace, key: key})
                .then(() => {
                    toast.deleted(key);
                    reloadKvs();
                });
        });
    }

    function removeKvs() {
        const groupedByNamespace = _groupBy(selection.value, "namespace");
        const withDeletePermissionGroupedKvs = Object.fromEntries(Object.entries(groupedByNamespace).filter(([namespace]) => authStore.user.isAllowed(permission.KVSTORE, action.DELETE, namespace)));
        const withDeletePermissionNamespaces = Object.keys(withDeletePermissionGroupedKvs);
        const withoutDeletePermissionNamespaces = Object.keys(groupedByNamespace).filter(n => !withDeletePermissionNamespaces.includes(n));
        toast.confirm(
            "kv.delete multiple.confirm" +
                (withoutDeletePermissionNamespaces.length === 0 ? "" : "\nkv.delete multiple.warning"),
            async () => {
                Object.entries(withDeletePermissionGroupedKvs).forEach(([namespace, kvs]) => {
                    namespacesStore
                        .deleteKvs({namespace, request: {keys: kvs.map(kv => kv.key)}})
                        .then(() => {
                            toast.deleted(`${kvs.length} KV(s) from ${namespace} namespace`);
                            reloadKvs();
                        });
                });
            });
    }

    async function reloadKvs() {
        if (!searchQuery.value) {
            kvs.value = [];
            const iterator = useNamespaces(100);
            let namespaces;
            let allKvs: any[] = [];
            do {
                namespaces = await iterator.next();
                for (const ns of namespaces) {
                    const kvFetch = (await namespacesStore.kvsList({id: ns.id})).map((kv: any) => ({...kv, namespace: ns.id}));
                    allKvs = allKvs.concat(kvFetch);
                }
            } while (namespaces.length > 0);
            kvs.value = allKvs;
        } else {
            namespaceIterator.value = undefined;
            await selectTable.value?.resetInfiniteScroll();
            kvs.value = [];
            fetchKvs();
        }
    }

    function saveKv(form: any) {
        form.validate((valid: boolean) => {
            if (!valid) {
                return false;
            }

            const type = kv.value.type;
            let value: any = kv.value.value;

            if (type === "STRING" || type === "DURATION") {
                value = value || "";
            } else if (type === "DATETIME") {
                value = new Date(value!).toISOString();
            } else if (type === "DATE") {
                value = new Date(value!).toISOString().split("T")[0];
            } else if (["NUMBER", "BOOLEAN", "JSON"].includes(type)) {
                value = JSON.stringify(value);
            }
        
            const contentType = ["DATE", "DATETIME"].includes(type) ? "text/plain" : "application/json";

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
                    reloadKvs();
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

    function handleSort({prop, order}: {prop: string, order: string}) {
        if (prop && order) {
            kvs.value?.sort((a, b) => {
                const [valueA, valueB] = [a[prop] ?? "", b[prop] ?? ""];
                const modifier = order === "ascending" ? 1 : -1;

                return typeof valueA === "string"
                    ? modifier * valueA.localeCompare(valueB)
                    : modifier * (valueA - valueB);
            });
        }
    }

    function refresh() {
        reloadKvs();
    }

    watch(addKvDrawerVisible, (newValue) => {
        if (!newValue) {
            resetKv();
        }
    });

    const formRef = ref();

    watch(() => kv.value.type, () => {
        if (formRef.value) {
            (formRef.value as any).clearValidate("value");
        }
    });

    watch(searchQuery, (newValue, oldValue) => {
        if (newValue !== oldValue) {
            reloadKvs();
        }
    });

    onMounted(() => {
        if (props.namespace !== undefined) {
            fetchKvs();
        }
    });

    defineExpose({
        updateVisibleColumns
    });
</script>
