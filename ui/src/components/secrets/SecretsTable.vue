<template>
    <div class="d-flex flex-column fill-height">
        <DataTable @page-changed="onPageChanged" ref="dataTable" :total="total">
            <template #top>
                <KSFilter
                    :configuration="secretsFilter"
                    :tableOptions="{
                        chart: {shown: false},
                        refresh: {shown: true, callback: loadData}
                    }"
                    :prefix="'secrets'"
                    :properties="{
                        shown: true,
                        columns: optionalColumns,
                        displayColumns,
                        storageKey: storageKey
                    }"
                    @update-properties="updateDisplayColumns"
                />
            </template>
            
            <template #table>
                <SelectTable
                    :data="secrets"
                    ref="selectTable"
                    :defaultSort="{prop: 'key', order: 'ascending'}"
                    tableLayout="auto"
                    fixed
                    :selectable="false"
                    @sort-change="onSort"
                    :no-data-text="$t('no_results.secrets')"
                    class="fill-height"
                    :rowKey="(row: any) => `${row.namespace}-${row.key}`"
                >
                    <el-table-column 
                        prop="key" 
                        sortable="custom"
                        :sortOrders="['ascending', 'descending']"
                        :label="keyOnly ? $t('secret.names') : $t('key')"
                    >
                        <template #default="scope">
                            <Id v-if="scope.row?.key !== undefined" :value="scope.row.key" :shrink="false" />
                        </template>
                    </el-table-column>

                    <el-table-column
                        v-for="col in visibleColumns"
                        :key="col.prop"
                        :prop="col.prop"
                        :label="col.label"
                        :sortable="col.prop === 'namespace' ? 'custom' : false"
                        :sortOrders="col.prop === 'namespace' ? ['ascending', 'descending'] : []"
                    >
                        <template #default="scope">
                            <template v-if="col.prop === 'namespace'">
                                <el-tag
                                    type="info"
                                    class="namespace-tag"
                                >
                                    <FolderOpenOutline />
                                    {{ scope.row?.namespace }}
                                </el-tag>
                            </template>
                            <template v-else-if="col.prop === 'description'">
                                {{ scope.row?.description }}
                            </template>
                            <template v-else-if="col.prop === 'tags'">
                                <Labels v-if="scope.row?.tags !== undefined" :labels="scope.row.tags" readOnly />
                            </template>
                        </template>
                    </el-table-column>

                    <el-table-column columnKey="locked" className="row-action">
                        <template #default="scope">
                            <el-tooltip 
                                v-if="scope.row?.namespace !== undefined && areNamespaceSecretsReadOnly"
                                transition=""
                                :hideAfter="0"
                                :persistent="false"
                                effect="light"
                            >
                                <template #content>
                                    <span v-html="$t('secret.isReadOnly')" />
                                </template>
                                <el-icon class="d-flex justify-content-center text-base">
                                    <Lock />
                                </el-icon>
                            </el-tooltip>
                        </template>
                    </el-table-column>

                    <el-table-column columnKey="copy" className="row-action">
                        <template #default="scope">
                            <el-tooltip :content="$t('copy_to_clipboard')">
                                <el-button 
                                    :icon="ContentCopy" 
                                    link
                                    @click="Utils.copy(`\{\{ secret('${scope.row?.key}') \}\}`)"
                                />
                            </el-tooltip>
                        </template>
                    </el-table-column>

                    <el-table-column 
                        v-if="!keyOnly && !paneView"
                        columnKey="update"
                        className="row-action"
                    >
                        <template #default="scope">
                            <el-button 
                                v-if="canUpdate(scope.row)"
                                :icon="FileDocumentEdit"
                                link
                                @click="updateSecretModal(scope.row)"
                            />
                        </template>
                    </el-table-column>

                    <el-table-column 
                        v-if="!keyOnly && !paneView"
                        columnKey="delete"
                        className="row-action"
                    >
                        <template #default="scope">
                            <el-button 
                                v-if="canDelete(scope.row)"
                                :icon="Delete"
                                link
                                @click="removeSecret(scope.row)"
                            />
                        </template>
                    </el-table-column>
                </SelectTable>
            </template>
        </DataTable>

        <Drawer
            v-if="addSecretDrawerVisible"
            v-model="addSecretDrawerVisible"
            :title="secretModalTitle"
        >
            <el-form class="ks-horizontal" :model="secret" :rules="rules" ref="form">
                <el-form-item
                    v-if="namespace === undefined"
                    :label="$t('namespace')"
                    prop="namespace"
                    required
                >
                    <NamespaceSelect
                        v-model="secret.namespace"
                        :readOnly="secret.update"
                        :includeSystemNamespace="true"
                        all
                    />
                </el-form-item>
                <el-form-item :label="$t('secret.key')" prop="key">
                    <el-input v-model="secret.key" :disabled="secret.update" required />
                </el-form-item>
                <el-form-item v-if="!secret.update" :label="$t('secret.name')" prop="value" required>
                    <MultilineSecret v-model="secret.value" :placeholder="secretModalTitle" />
                </el-form-item>
                <el-form-item v-if="secret.update" :label="$t('secret.name')" prop="value">
                    <el-col :span="20">
                        <MultilineSecret 
                            v-model="secret.value"
                            :placeholder="secretModalTitle"
                            :disabled="!secret.updateValue"
                        />
                    </el-col>
                    <el-col class="px-2" :span="4">
                        <el-switch
                            size="large"
                            inlinePrompt
                            v-model="secret.updateValue"
                            :activeIcon="PencilOutline"
                            :inactiveIcon="PencilOff"
                        />
                    </el-col>
                </el-form-item>
                <el-form-item :label="$t('secret.description')" prop="description">
                    <el-input 
                        v-model="secret.description"
                        :placeholder="$t('secret.descriptionPlaceholder')"
                        required
                    />
                </el-form-item>
                <el-form-item :label="$t('secret.tags')" prop="tags">
                    <el-row :gutter="20" v-for="(tag, index) in secret.tags" :key="index">
                        <el-col :span="8">
                            <el-input required v-model="tag.key" :placeholder="$t('key')" />
                        </el-col>
                        <el-col :span="12">
                            <el-input required v-model="tag.value" :placeholder="$t('value')" />
                        </el-col>
                        <el-button-group class="d-flex flex-nowrap">
                            <el-button
                                :icon="Delete"
                                @click="removeSecretTag(index)"
                            />
                        </el-button-group>
                    </el-row>
                    <el-button :icon="Plus" @click="addSecretTag" type="primary">
                        {{ $t('secret.addTag') }}
                    </el-button>
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button :icon="ContentSave" @click="saveSecret(form)" type="primary">
                    {{ $t('save') }}
                </el-button>
            </template>
        </Drawer>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import type {FormInstance} from "element-plus";
    import {ref, computed, watch, onMounted, useTemplateRef} from "vue";
    import _merge from "lodash/merge";

    import Lock from "vue-material-design-icons/Lock.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import PencilOff from "vue-material-design-icons/PencilOff.vue";
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import PencilOutline from "vue-material-design-icons/PencilOutline.vue";
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";

    import Id from "../Id.vue";
    import Drawer from "../Drawer.vue";
    import Labels from "../layout/Labels.vue";
    import KSFilter from "../filter/components/KSFilter.vue";
    import DataTable from "../layout/DataTable.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import MultilineSecret from "./MultilineSecret.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";

    import action from "../../models/action";
    import permission from "../../models/permission";
    import Utils from "../../utils/utils";
    import {useToast} from "../../utils/toast";
    import {storageKeys} from "../../utils/constants";
    import {useSecretsStore} from "../../stores/secrets";
    import {useAuthStore} from "override/stores/auth";
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useSecretsFilter} from "../filter/configurations";
    import {useTableColumns} from "../../composables/useTableColumns";
    import {DataTableRef, useDataTableActions} from "../../composables/useDataTableActions";
    
    const secretsFilter = useSecretsFilter();

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
        includeInherited: false
    });

    const emit = defineEmits<{
        "update:addSecretModalVisible": [value: boolean];
        "update:isSecretReadOnly": [value: boolean];
        hasData: [value: boolean];
    }>();

    const {t} = useI18n();
    const toast = useToast();
    const route = useRoute();
    const authStore = useAuthStore();
    const secretsStore = useSecretsStore();
    const namespacesStore = useNamespacesStore();

    const form = ref<FormInstance>();
    const dataTable = useTemplateRef<DataTableRef>("dataTable");
    const selectTable = ref<InstanceType<typeof SelectTable>>();

    const total = ref(0);
    const hasData = ref<boolean>();
    const areNamespaceSecretsReadOnly = ref(false);
    const secrets = ref<(NamespaceSecret & {namespace?: string})[]>();

    const secret = ref<SecretForm>({
        namespace: props.namespace,
        key: undefined,
        value: "",
        description: undefined,
        tags: [{key: undefined, value: undefined}],
        update: undefined,
        updateValue: undefined
    });

    const storageKey = storageKeys.DISPLAY_SECRETS_COLUMNS;

    const optionalColumns = computed(() => {
        const columns = [
            {
                label: t("namespace"), 
                prop: "namespace", 
                default: true, 
                description: t("filter.table_column.secrets.namespace")
            },
            {
                label: t("description"), 
                prop: "description", 
                default: true, 
                description: t("filter.table_column.secrets.description")
            },
            {
                label: t("tags"), 
                prop: "tags", 
                default: true,
                description: t("filter.table_column.secrets.tags")
            }
        ];
        
        return columns.filter(col => {
            if (col.prop === "namespace" && !(props.namespace === undefined || props.namespaceColumn)) return false;
            if (col.prop === "description" && props.keyOnly) return false;
            if (col.prop === "tags" && (props.keyOnly || props.paneView)) return false;
            return true;
        });
    });

    const {visibleColumns: displayColumns, updateVisibleColumns: updateDisplayColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey: storageKey
    });

    const visibleColumns = computed(() => 
        displayColumns.value
            ?.map(prop => optionalColumns.value?.find(c => c.prop === prop))
            ?.filter(Boolean) as any[]
    );

    const secretModalTitle = computed(() => {
        return secret.value?.update 
            ? t("secret.update", {name: secret.value?.key}) 
            : t("secret.add");
    });

    const addSecretDrawerVisible = computed({
        get() {
            return props.addSecretModalVisible;
        },
        set(newValue: boolean) {
            emit("update:addSecretModalVisible", newValue);
        }
    });

    const checkSecretValue = (_rule: any, _value: any, callback: any) => {
        if (secret.value?.updateValue && (secret.value.value === undefined || secret.value.value.length === 0)) {
            callback(new Error("Value must not be empty."));
        } else {
            callback();
        }
    };

    const checkSecretTags = (_rule: any, _value: any, callback: any) => {
        const keys = secret.value?.tags?.map((it) => it.key);

        if (secret.value?.tags?.length === 1) {
            if (secret.value?.tags?.[0]?.key === undefined && secret.value?.tags?.[0]?.value === undefined) {
                callback();
                return;
            }
        }

        const nullKeys = keys?.filter(item => item === undefined);
        const duplicateKeys = keys?.filter((item, index) => keys.indexOf(item) !== index);

        if (nullKeys?.length > 0) {
            callback(new Error("Tag key must not be empty."));
        } else if (duplicateKeys?.length > 0) {
            callback(new Error("Duplicate tags for keys: " + Array.from(new Set(duplicateKeys))));
        } else {
            callback();
        }
    };

    const rules = {
        key: [
            {required: true, trigger: "change"}
        ],
        value: [
            {
                validator: checkSecretValue,
                trigger: ["blur"],
                required: false
            }
        ],
        secret: [
            {required: true, trigger: "change"}
        ],
        tags: [
            {
                validator: checkSecretTags,
                trigger: ["blur"],
                required: false
            }
        ]
    };

    const canUpdate = (secret: NamespaceSecret & {namespace?: string}) => {
        return secret?.namespace !== undefined &&
            authStore.user?.isAllowed(permission.SECRET, action.UPDATE, secret.namespace) &&
            !areNamespaceSecretsReadOnly.value;
    };

    const canDelete = (secret: NamespaceSecret & {namespace?: string}) => {
        return secret?.namespace !== undefined &&
            authStore.user?.isAllowed(permission.SECRET, action.DELETE, secret.namespace) &&
            !areNamespaceSecretsReadOnly.value;
    };

    const loadQuery = (base: any) => {
        const queryFilter = queryWithFilter();
        return _merge(base, queryFilter);
    };

    const loadData = async (callback?: () => void) => {
        try {
            const secretsResponse = await secretsStore.find(loadQuery({
                size: parseInt(String(route.query?.size ?? 25)),
                page: parseInt(String(route.query?.page ?? 1)),
                sort: String(route.query?.sort ?? "key:asc"),
                ...(props.namespace === undefined ? {} : {
                    filters: {
                        namespace: {
                            EQUALS: props.namespace
                        }
                    }
                })
            }));

            emit("update:isSecretReadOnly", secretsResponse.readOnly ?? false);
            
            let allSecrets = secretsResponse.results ?? [];

            if (props.includeInherited && props.namespace) {
                const parentNamespaces = Utils.getParentNamespaces(props.namespace).slice(0, -1);
                
                for (const parentNs of parentNamespaces) {
                    const parentSecretsResponse = await secretsStore.find(loadQuery({
                        filters: {
                            namespace: {
                                EQUALS: parentNs
                            }
                        }
                    }));

                    const parentSecrets = parentSecretsResponse?.results ?? [];
                    if (parentSecrets.length > 0) {
                        const currentKeys = new Set(allSecrets.map((s: any) => s?.key).filter(Boolean));
                        const newSecrets = parentSecrets.filter(
                            (s: any) => s?.key && !currentKeys.has(s.key)
                        );
                        allSecrets.push(...newSecrets);
                    }
                }
            }

            hasData.value = (allSecrets.length ?? 0) !== 0;
            areNamespaceSecretsReadOnly.value = secretsResponse.readOnly ?? false;
            secrets.value = allSecrets;
            total.value = allSecrets.length;
        } finally {
            if (callback) callback();
        }
    };

    const {onPageChanged, queryWithFilter, onSort} = useDataTableActions({
        dataTableRef: dataTable,
        loadData
    });

    const updateSecretModal = (secretData: NamespaceSecret) => {
        secret.value.namespace = secretData?.namespace;
        secret.value.key = secretData?.key;
        secret.value.description = secretData?.description;
        secret.value.tags = secretData?.tags?.map((x: any) => ({...x})) ?? [{key: undefined, value: undefined}];
        secret.value.update = true;
        secret.value.updateValue = false;
        addSecretDrawerVisible.value = true;
    };

    const addSecretTag = () => {
        secret.value?.tags?.push({key: "" as any, value: "" as any});
    };

    const removeSecretTag = (index: number) => {
        secret.value?.tags?.splice(index, 1);
    };

    const removeSecret = ({key, namespace}: {key: string; namespace: string}) => {
        toast.confirm(t("delete confirm", {name: key}), () => {
            return namespacesStore
                .deleteSecrets({namespace, key})
                .then(() => {
                    toast.deleted(key);
                })
                .then(() => loadData());
        });
    };

    const isSecretValueUpdated = () => {
        return !secret.value?.update || secret.value?.updateValue;
    };

    const saveSecret = (formRef: FormInstance | undefined) => {
        if (!formRef) return;

        formRef.validate((valid: boolean) => {
            if (!valid) {
                return;
            }

            const secretData: any = {
                key: secret.value?.key,
                description: secret.value?.description,
                tags: secret.value?.tags
                    ?.map(item => item.value !== undefined ? item : {key: item.key, value: ""})
                    ?.filter(item => item.key !== undefined)
            };

            if (isSecretValueUpdated()) {
                secretData.value = secret.value?.value;
            }

            const actionMethod = isSecretValueUpdated()
                ? namespacesStore.createSecrets
                : namespacesStore.patchSecret;

            actionMethod({namespace: secret.value?.namespace as string, secret: secretData})
                .then(() => {
                    secret.value!.update = true;
                    toast.saved(secret.value?.key || "");
                    addSecretDrawerVisible.value = false;
                    resetForm();
                    return loadData();
                });
        });
    };

    const resetForm = () => {
        secret.value = {
            namespace: props.namespace,
            key: undefined,
            value: "",
            description: undefined,
            tags: [{key: undefined, value: undefined}],
            update: undefined,
            updateValue: undefined
        };
    };

    watch(() => props.addSecretModalVisible, (newValue) => {
        if (!newValue) {
            resetForm();
        }
    });

    watch(hasData, (newValue, oldValue) => {
        if (oldValue !== newValue) {
            emit("hasData", newValue!);
        }
    });

    onMounted(() => {
        updateDisplayColumns(
            localStorage.getItem(`columns_${storageKey}`)?.split(",") ||
                optionalColumns.value?.filter(col => col.default).map(col => col.prop)
        );
    });
</script>
<style scoped lang="scss">
    .namespace-tag {
        background-color: var(--ks-log-background-debug) !important;
        color: var(--ks-log-content-debug);
        border: 1px solid var(--ks-log-border-debug);
        padding: 0 6px;

        :deep(.el-tag__content) {
            display: flex;
            align-items: center;
            gap: 4px;
        }
    }
</style>
