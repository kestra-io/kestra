<template>
    <div class="d-flex flex-column fill-height">
        <ks-data-table
            ref="dataTable"
            :loadData="loadData"
            :data="secrets"
            :total="total"
            :defaultSort="{prop: 'key', order: 'ascending'}"
            :selectable="false"
            @page-changed="({page, size}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
            @sort-change="({prop, order}: {prop: string; order: string}) => router.push({query: {...route.query, sort: `${prop}:${order === 'ascending' ? 'asc' : 'desc'}`}})"
            :no-data-text="$t('no_results.secrets')"
            class="fill-height"
            :rowKey="(row: any) => `${row.namespace}-${row.key}`"
        >
            <template #top>
                <KSFilter
                    :configuration="secretsFilter"
                    :tableOptions="{
                        chart: {shown: false},
                        refresh: {shown: true, callback: () => dataTable.value?.reload()}
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

            <ks-table-column
                prop="key"
                sortable="custom"
                :sortOrders="['ascending', 'descending']"
                :label="keyOnly ? $t('secret.names') : $t('key')"
            >
                <template #default="scope">
                    <KsId v-if="scope.row?.key !== undefined" :value="scope.row.key" :shrink="false" />
                </template>
            </ks-table-column>

            <ks-table-column
                v-for="col in visibleColumns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
                :sortable="col.prop === 'namespace' ? 'custom' : false"
                :sortOrders="col.prop === 'namespace' ? ['ascending', 'descending'] : []"
            >
                <template #default="scope">
                    <template v-if="col.prop === 'namespace'">
                        <ks-tag
                            type="info"
                            class="namespace-tag"
                        >
                            <FolderOpenOutline />
                            {{ scope.row?.namespace }}
                        </ks-tag>
                    </template>
                    <template v-else-if="col.prop === 'description'">
                        {{ scope.row?.description }}
                    </template>
                    <template v-else-if="col.prop === 'tags'">
                        <Labels v-if="scope.row?.tags !== undefined" :labels="scope.row.tags" readOnly />
                    </template>
                </template>
            </ks-table-column>

            <ks-table-column columnKey="locked" className="row-action">
                <template #default="scope">
                    <ks-tooltip
                        v-if="scope.row?.namespace !== undefined && areNamespaceSecretsReadOnly"
                    >
                        <template #content>
                            <span v-html="$t('secret.isReadOnly')" />
                        </template>
                        <ks-icon class="d-flex justify-content-center">
                            <Lock />
                        </ks-icon>
                    </ks-tooltip>
                </template>
            </ks-table-column>

            <ks-table-column columnKey="copy" className="row-action">
                <template #default="scope">
                    <KsIconButton
                        :tooltip="$t('copy_to_clipboard')"
                        placement="left"
                        @click="Utils.copy(`\{\{ secret('${scope.row?.key}') \}\}`)"
                    >
                        <ContentCopy />
                    </KsIconButton>
                </template>
            </ks-table-column>

            <ks-table-column
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
            </ks-table-column>

            <ks-table-column
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
            </ks-table-column>
        </ks-data-table>

        <ks-drawer
            v-if="addSecretDrawerVisible"
            v-model="addSecretDrawerVisible"
            :title="secretModalTitle"
        >
            <ks-form class="ks-horizontal" :model="secret" :rules="rules" ref="form">
                <ks-form-item
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
                </ks-form-item>
                <ks-form-item :label="$t('secret.key')" prop="key">
                    <ks-input v-model="secret.key" :disabled="secret.update" required />
                </ks-form-item>
                <ks-form-item v-if="!secret.update" :label="$t('secret.name')" prop="value" required>
                    <KsPassword v-model="secret.value" :placeholder="secretModalTitle" />
                </ks-form-item>
                <ks-form-item v-if="secret.update" :label="$t('secret.name')" prop="value">
                    <ks-col :span="20">
                        <KsPassword
                            v-model="secret.value"
                            :placeholder="secretModalTitle"
                            :disabled="!secret.updateValue"
                        />
                    </ks-col>
                    <ks-col class="px-2" :span="4">
                        <ks-switch
                            size="large"
                            inlinePrompt
                            v-model="secret.updateValue"
                            :activeIcon="PencilOutline"
                            :inactiveIcon="PencilOff"
                        />
                    </ks-col>
                </ks-form-item>
                <ks-form-item :label="$t('secret.description')" prop="description">
                    <ks-input
                        v-model="secret.description"
                        :placeholder="$t('secret.descriptionPlaceholder')"
                        required
                    />
                </ks-form-item>
                <ks-form-item :label="$t('secret.tags')" prop="tags">
                    <ks-row :gutter="20" v-for="(tag, index) in secret.tags" :key="index">
                        <ks-col :span="8">
                            <ks-input required v-model="tag.key" :placeholder="$t('key')" />
                        </ks-col>
                        <ks-col :span="12">
                            <ks-input required v-model="tag.value" :placeholder="$t('value')" />
                        </ks-col>
                        <ks-button-group class="d-flex flex-nowrap">
                            <ks-button
                                :icon="Delete"
                                @click="removeSecretTag(index)"
                            />
                        </ks-button-group>
                    </ks-row>
                    <ks-button :icon="Plus" @click="addSecretTag" type="default">
                        {{ $t('secret.addTag') }}
                    </ks-button>
                </ks-form-item>
            </ks-form>

            <template #footer>
                <ks-button :icon="ContentSave" @click="saveSecret(form)" type="primary">
                    {{ $t('save') }}
                </ks-button>
            </template>
        </ks-drawer>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import type {FormInstance} from "@kestra-io/ui-design-system";
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

    import {KsId, KsIconButton, KsPassword} from "@kestra-io/ui-design-system";
    import Labels from "../layout/Labels.vue";
    import {KsFilter as KSFilter} from "@kestra-io/ui-design-system";
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
    const router = useRouter();
    const authStore = useAuthStore();
    const secretsStore = useSecretsStore();
    const namespacesStore = useNamespacesStore();

    const form = ref<FormInstance>();

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

    const dataTable = useTemplateRef("dataTable");

    const loadQuery = (base: any) => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query;
        return _merge(base, filters);
    };

    const loadData = async ({page, size, sort}: {page: number; size: number; sort?: string}) => {
        const secretsResponse = await secretsStore.find(loadQuery({
            size,
            page,
            sort: sort ?? String(route.query.sort ?? "key:asc"),
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
        total.value = secretsResponse.total ?? 0;
    };

    const filterQuery = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query;
        return filters;
    });

    watch(filterQuery, () => {
        dataTable.value?.resetAndReload();
    }, {deep: true});

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
                .then(() => dataTable.value?.reload());
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
                    dataTable.value?.reload();
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

        :deep(.kel-tag__content) {
            display: flex;
            align-items: center;
            gap: 4px;
        }
    }
</style>
