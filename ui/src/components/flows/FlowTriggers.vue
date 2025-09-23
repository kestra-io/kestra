<template>
    <KestraFilter
        v-if="triggersWithType.length"
        prefix="flow_triggers"
        readOnly
        :buttons="{
            refresh: {shown: true, callback: loadData},
            settings: {shown: false}
        }"
        legacyQuery
    />

    <el-table
        v-if="triggersWithType.length"
        v-bind="$attrs"
        :data="triggersWithType"
        tableLayout="auto"
        defaultExpandAll
    >
        <el-table-column type="expand">
            <template #default="props">
                <LogsWrapper class="m-3" :filters="{...props.row, triggerId: props.row.id}" purgeFilters :withCharts="false" embed />
            </template>
        </el-table-column>
        <el-table-column prop="id" :label="$t('id')">
            <template #default="scope">
                <code>
                    {{ scope.row.id }}
                </code>
            </template>
        </el-table-column>

        <el-table-column prop="type" :label="$t('type')" />

        <el-table-column prop="workerId" :label="$t('workerId')">
            <template #default="scope">
                <Id
                    :value="scope.row.workerId"
                    :shrink="true"
                />
            </template>
        </el-table-column>

        <el-table-column prop="nextExecutionDate" :label="$t('next execution date')">
            <template #default="scope">
                <DateAgo :inverted="true" :date="scope.row.nextExecutionDate" />
            </template>
        </el-table-column>

        <el-table-column columnKey="backfill" v-if="userCan(action.UPDATE) || userCan(action.CREATE)">
            <template #header>
                {{ $t("backfill") }}
            </template>
            <template #default="scope">
                <BackfillCell
                    :trigger="scope.row"
                    :canCreate="userCan(action.CREATE)"
                    :canUpdate="userCan(action.UPDATE)"
                    @open-backfill="openBackfill(scope.row)"
                    @updated="(newTrigger) => { $toast().saved(newTrigger.id); triggers = triggers.map(t => t.id === newTrigger.id ? newTrigger : t) }"
                />
            </template>
        </el-table-column>

        <el-table-column columnKey="disable" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-switch
                    v-if="canBeDisabled(scope.row)"
                    size="small"
                    :activeText="$t('enabled')"
                    :modelValue="!scope.row.disabled"
                    @change="setDisabled(scope.row, $event)"
                    class="switch-text"
                    :activeActionIcon="Check"
                />
            </template>
        </el-table-column>

        <el-table-column columnKey="restart" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-button size="small" v-if="scope.row.evaluateRunningDate" @click="restart(scope.row)">
                    <Kicon :tooltip="$t('restart trigger.button')">
                        <Restart />
                    </Kicon>
                </el-button>
            </template>
        </el-table-column>

        <el-table-column columnKey="unlock" className="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-button size="small" v-if="scope.row.executionId" @click="unlock(scope.row)">
                    <Kicon :tooltip="$t('unlock trigger.button')">
                        <LockOff />
                    </Kicon>
                </el-button>
            </template>
        </el-table-column>

        <el-table-column>
            <template #default="scope">
                <TriggerAvatar :flow="flowStore.flow" :triggerId="scope.row.id" />
            </template>
        </el-table-column>

        <el-table-column columnKey="action" className="row-action">
            <template #default="scope">
                <el-button size="small" @click="triggerId = scope.row.id; isOpen = true">
                    <Kicon :tooltip="$t('details')" placement="left">
                        <TextSearch />
                    </Kicon>
                </el-button>
            </template>
        </el-table-column>
    </el-table>

    <div v-if="triggersWithType.length" class="mt-4">
        <el-button
            @click="addNewTrigger"
            :icon="Plus"
            class="border-0 p-3"
        >
            {{ $t('no_code.creation.triggers') }}
        </el-button>
    </div>

    <Empty
        v-else
        type="triggers"
    >
        <template #button>
            <el-button
                type="primary"
                @click="addNewTrigger"
                :icon="Plus"
                class="mt-3"
            >
                {{ $t('no_code.creation.triggers') }}
            </el-button>
        </template>
    </Empty>

    <BackfillDialog
        v-model="isBackfillOpen"
        v-if="selectedTrigger"
        :namespace="flowStore.flow.namespace"
        :flowId="flowStore.flow.id"
        :trigger="selectedTrigger"
        @updated="(newTrigger) => { $toast().saved(newTrigger.id); triggers = triggers.map(t => t.id === newTrigger.id ? newTrigger : t) }"
    />

    <Drawer
        v-if="isOpen"
        v-model="isOpen"
    >
        <template #header>
            <code>{{ triggerId }}</code>
        </template>

        <Markdown v-if="triggerDefinition && triggerDefinition.description" :source="triggerDefinition.description" />
        <Vars :data="modalData" />
    </Drawer>
</template>

<script lang="ts" setup>
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import Restart from "vue-material-design-icons/Restart.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Id from "../Id.vue";
    import TriggerAvatar from "./TriggerAvatar.vue";

    import KestraFilter from "../filter/KestraFilter.vue";
    import Empty from "../layout/empty/Empty.vue";
    import Markdown from "../layout/Markdown.vue";
    import Kicon from "../Kicon.vue"
    import DateAgo from "../layout/DateAgo.vue";
    import Vars from "../executions/Vars.vue";
    import Drawer from "../Drawer.vue";
    import BackfillCell from "./BackfillCell.vue";
    import BackfillDialog from "./BackfillDialog.vue";
</script>

<script lang="ts">
    import permission from "../../models/permission";
    import action from "../../models/action";
    import LogsWrapper from "../logs/LogsWrapper.vue";
    import _isEqual from "lodash/isEqual";
    import {storageKeys} from "../../utils/constants";
    import {mapStores} from "pinia";
    import {useTriggerStore} from "../../stores/trigger";
    import {useAuthStore} from "override/stores/auth";
    import {useFlowStore} from "../../stores/flow";

    export default {
        inheritAttrs: false,
        props:{
            embed: {
                type: Boolean,
                default: false
            },
            backfillRouteName: {type: String, default: "admin/triggers"}
        },
        data() {
            return {
                triggerId: undefined,
                isOpen: false,
                isBackfillOpen: false,
                triggers: [],
                selectedTrigger: null,
            }
        },
        created() {
            this.loadData();
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name && !_isEqual(newValue.query, oldValue.query)) {
                    this.loadData();
                }
            }
        },
        computed: {
            ...mapStores(useTriggerStore, useFlowStore, useAuthStore),
            query() {
                return Array.isArray(this.$route.query.q) ? this.$route.query.q[0] : this.$route.query.q;
            },
            modalData() {
                return Object
                    .entries(this.triggersWithType.filter(trigger => trigger.triggerId === this.triggerId)[0])
                    .filter(([key]) => !["tenantId", "namespace", "flowId", "flowRevision", "triggerId", "description"].includes(key))
                    .reduce(
                        (map, currentValue) => {
                            map[currentValue[0]] = currentValue[1];
                            return map;
                        },
                        {},
                    );
            },
            triggerDefinition() {
                return this.flowStore.flow.triggers.find(trigger => trigger.id === this.triggerId);
            },
            triggersWithType() {
                if(!this.flowStore.flow.triggers) return [];

                let flowTriggers = this.flowStore.flow.triggers.map(trigger => {
                    return {...trigger, sourceDisabled: trigger.disabled ?? false}
                })
                if (flowTriggers) {
                    const triggers = flowTriggers.map(flowTrigger => {
                        let pollingTrigger = this.triggers.find(trigger => trigger.triggerId === flowTrigger.id)
                        return {...flowTrigger, ...pollingTrigger}
                    })

                    return !this.query ? triggers : triggers.filter(trigger => trigger.id.includes(this.query))
                }
                return this.triggers
            },
            editorViewType() {
                return localStorage.getItem(storageKeys.EDITOR_VIEW_TYPE) === "NO_CODE";
            },
            triggerStore() {
                return useTriggerStore();
            },
        },
        methods: {
            userCan(action) {
                return this.authStore.user?.isAllowed(permission.EXECUTION, action ? action : action.READ, this.flowStore.flow.namespace);
            },
            loadData() {
                if(!this.triggersWithType.length) return;

                this.triggerStore
                    .find({namespace: this.flowStore.flow.namespace, flowId: this.flowStore.flow.id, size: this.triggersWithType.length, q: this.query})
                    .then(triggers => this.triggers = triggers.results);
            },
            openBackfill(trigger) {
                this.selectedTrigger = trigger;
                this.isBackfillOpen = true;
            },
            pauseBackfill(trigger) {
                this.triggerStore.pauseBackfill(trigger)
                    .then(newTrigger => {
                        this.$toast().saved(newTrigger.id);
                        this.triggers = this.triggers.map(t => {
                            if (t.id === newTrigger.id) {
                                return newTrigger
                            }
                            return t
                        })
                    })
            },
            unpauseBackfill(trigger) {
                this.triggerStore.unpauseBackfill(trigger)
                    .then(newTrigger => {
                        this.$toast().saved(newTrigger.id);
                        this.triggers = this.triggers.map(t => {
                            if (t.id === newTrigger.id) {
                                return newTrigger
                            }
                            return t
                        })
                    })
            },
            deleteBackfill(trigger) {
                this.triggerStore.deleteBackfill(trigger)
                    .then(newTrigger => {
                        this.$toast().saved(newTrigger.id);
                        this.triggers = this.triggers.map(t => {
                            if (t.id === newTrigger.id) {
                                return newTrigger
                            }
                            return t
                        })
                    })
            },
            setDisabled(trigger, value) {
                this.triggerStore.update({...trigger, disabled: !value})
                    .then(newTrigger => {
                        this.$toast().saved(newTrigger.id);
                        this.triggers = this.triggers.map(t => {
                            if (t.id === newTrigger.id) {
                                return newTrigger
                            }
                            return t
                        })
                    })
            },
            unlock(trigger) {
                this.triggerStore.unlock({
                    namespace: trigger.namespace,
                    flowId: trigger.flowId,
                    triggerId: trigger.triggerId
                }).then(newTrigger => {
                    this.$toast().saved(newTrigger.id);
                    this.triggers = this.triggers.map(t => {
                        if (t.id === newTrigger.id) {
                            return newTrigger
                        }
                        return t
                    })
                })
            },
            restart(trigger) {
                this.triggerStore.restart({
                    namespace: trigger.namespace,
                    flowId: trigger.flowId,
                    triggerId: trigger.triggerId
                }).then(newTrigger => {
                    this.$toast().saved(newTrigger.id);
                    this.triggers = this.triggers.map(t => {
                        if (t.id === newTrigger.id) {
                            return newTrigger
                        }
                        return t
                    })
                })
            },
            canBeDisabled(trigger) {
                return this.triggers.map(trigg => trigg.triggerId).includes(trigger.id)
                    && !trigger.sourceDisabled;
            },
            addNewTrigger() {
                localStorage.setItem(storageKeys.EDITOR_VIEW_TYPE, "NO_CODE");

                const baseUrl = {
                    name: "flows/update",
                    params: {
                        tenant: this.$route.params.tenant,
                        namespace: this.flowStore.flow.namespace,
                        id: this.flowStore.flow.id,
                        tab: "edit"
                    }
                };

                if (this.editorViewType) {
                    const route = {
                        ...baseUrl,
                        query: {
                            section: "triggers"
                        }
                    };

                    this.$nextTick(() => {
                        this.$router.push(route).then(() => {
                            this.$router.replace({
                                ...route,
                                query: {
                                    ...route.query,
                                }
                            });
                        });
                    });
                } else {
                    this.$router.push(baseUrl);
                }
            }
        }
    };
</script>

<style scoped>
    .pickers {
        display: flex;
        justify-content: space-between;

        .small-picker {
            width: 49%;
        }
    }

    .backfill-cell {
        display: flex;
        align-items: center;
    }

    .progress-cell {
        width: 200px;
        margin-right: 1em;
    }

    :deep(.markdown) {
        p {
            margin-bottom: auto;
        }
    }
</style>