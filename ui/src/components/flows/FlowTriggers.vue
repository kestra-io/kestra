<template>
    <el-table
        v-bind="$attrs"
        :data="triggersWithType"
        stripe
        table-layout="auto"
        @row-dblclick="triggerId = $event.id; isOpen = true"
    >
        <el-table-column prop="id" :label="$t('id')">
            <template #default="scope">
                <code>
                    {{ scope.row.id }}
                </code>
            </template>
        </el-table-column>

        <el-table-column prop="type" :label="$t('type')" />

        <el-table-column prop="nextExecutionDate" :label="$t('next execution date')">
            <template #default="scope">
                <date-ago :inverted="true" :date="scope.row.nextExecutionDate" />
            </template>
        </el-table-column>

        <el-table-column column-key="backfill" v-if="userCan(action.UPDATE) || userCan(action.CREATE)">
            <template #header>
                {{ $t("backfill") }}
                <refresh-button
                    :can-auto-refresh="true"
                    @refresh="loadData"
                    size="small"
                    custom-class="mx-1"
                />
            </template>
            <template #default="scope">
                <el-button
                    :icon="CalendarCollapseHorizontalOutline"
                    v-if="scheduleClassName === scope.row.type && !scope.row.backfill && userCan(action.CREATE)"
                    @click="setBackfillModal(scope.row, true)"
                    :disabled="scope.row.disabled"
                    size="small"
                    type="primary"
                >
                    {{ $t("backfill executions") }}
                </el-button>
                <template v-else-if="scheduleClassName === scope.row.type && userCan(action.UPDATE)">
                    <div class="backfill-cell">
                        <div class="progress-cell">
                            <el-progress
                                :percentage="backfillProgression(scope.row.backfill)"
                                :status="scope.row.backfill.paused ? 'warning' : ''"
                                :stroke-width="12"
                                :show-text="!scope.row.backfill.paused"
                                :striped="!scope.row.backfill.paused"
                                striped-flow
                            />
                        </div>
                        <template v-if="!scope.row.backfill.paused">
                            <el-button size="small" @click="pauseBackfill(scope.row)">
                                <kicon :tooltip="$t('pause backfill')">
                                    <Pause />
                                </kicon>
                            </el-button>
                        </template>
                        <template v-else-if="userCan(action.UPDATE)">
                            <el-button size="small" @click="unpauseBackfill(scope.row)">
                                <kicon :tooltip="$t('continue backfill')">
                                    <Play />
                                </kicon>
                            </el-button>

                            <el-button size="small" @click="deleteBackfill(scope.row)">
                                <kicon :tooltip="$t('delete backfill')">
                                    <Delete />
                                </kicon>
                            </el-button>
                        </template>
                    </div>
                </template>
            </template>
        </el-table-column>

        <el-table-column column-key="disable" class-name="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-switch
                    size="small"
                    :active-text="$t('enabled')"
                    :model-value="!scope.row.disabled"
                    @change="setDisabled(scope.row, $event)"
                    class="switch-text"
                    :active-action-icon="Check"
                />
            </template>
        </el-table-column>

        <el-table-column column-key="unlock" class-name="row-action" v-if="userCan(action.UPDATE)">
            <template #default="scope">
                <el-button size="small" v-if="scope.row.executionId" @click="unlock(scope.row)">
                    <kicon :tooltip="$t('unlock trigger.button')">
                        <lock-off />
                    </kicon>
                </el-button>
            </template>
        </el-table-column>

        <el-table-column column-key="action" class-name="row-action">
            <template #default="scope">
                <el-button size="small" @click="triggerId = scope.row.id; isOpen = true">
                    <kicon :tooltip="$t('details')" placement="left">
                        <TextSearch />
                    </kicon>
                </el-button>
            </template>
        </el-table-column>
    </el-table>

    <el-dialog v-model="isBackfillOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <span v-html="$t('backfill executions')" />
        </template>
        <el-form :model="backfill" label-position="top">
            <div class="pickers">
                <div class="small-picker">
                    <el-form-item label="Start">
                        <el-date-picker
                            v-model="backfill.start"
                            type="datetime"
                            placeholder="Start"
                            :disabled-date="time => new Date() < time || backfill.end ? time > backfill.end : false"
                        />
                    </el-form-item>
                </div>
                <div class="small-picker">
                    <el-form-item label="End">
                        <el-date-picker
                            v-model="backfill.end"
                            type="datetime"
                            placeholder="End"
                            :disabled-date="time => new Date() < time || backfill?.start > time"
                        />
                    </el-form-item>
                </div>
            </div>
        </el-form>
        <flow-run
            @update-inputs="backfill.inputs = $event"
            @update-labels="backfill.labels = $event"
            :redirect="false"
            :embed="true"
        />
        <template #footer>
            <el-button
                type="primary"
                @click="postBackfill()"
                :disabled="checkBackfill()"
            >
                {{ $t("execute backfill") }}
            </el-button>
        </template>
    </el-dialog>

    <drawer
        v-if="isOpen"
        v-model="isOpen"
    >
        <template #header>
            <code>{{ triggerId }}</code>
        </template>

        <markdown v-if="triggerDefinition && triggerDefinition.description" :source="triggerDefinition.description" />
        <vars :data="modalData" />
    </drawer>
</template>

<script setup>
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import Pause from "vue-material-design-icons/Pause.vue";
    import Play from "vue-material-design-icons/Play.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue"
    import FlowRun from "./FlowRun.vue";
    import RefreshButton from "../layout/RefreshButton.vue";
</script>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapGetters, mapState} from "vuex";
    import Kicon from "../Kicon.vue"
    import DateAgo from "../layout/DateAgo.vue";
    import Vars from "../executions/Vars.vue";
    import Drawer from "../Drawer.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import moment from "moment";

    export default {
        components: {Markdown, Kicon, DateAgo, Vars, Drawer},
        data() {
            return {
                triggerId: undefined,
                isOpen: false,
                isBackfillOpen: false,
                triggers: [],
                // className to check to display the backfill button
                scheduleClassName: "io.kestra.core.models.triggers.types.Schedule",
                selectedTrigger: null,
                backfill: {
                    start: null,
                    end: null,
                    inputs: null,
                    labels: []
                }
            }
        },
        created() {
            this.loadData();
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapGetters("flow", ["flow"]),
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
                return this.flow.triggers.find(trigger => trigger.id === this.triggerId);
            },
            triggersWithType() {
                let flowTriggers = this.flow.triggers
                if (flowTriggers) {
                    return flowTriggers.map(flowTrigger => {
                        let pollingTrigger = this.triggers.find(trigger => trigger.triggerId === flowTrigger.id)
                        return {...flowTrigger, ...(pollingTrigger || {})}
                    })
                }
                return this.triggers
            },
            cleanBackfill() {
                return {...this.backfill, labels: this.backfill.labels.filter(label => label.key && label.value)}
            }
        },
        methods: {
            userCan(action) {
                return this.user.isAllowed(permission.EXECUTION, action ? action : action.READ, this.flow.namespace);
            },
            loadData() {
                this.$store
                    .dispatch("trigger/find", {namespace: this.flow.namespace, flowId: this.flow.id, size: this.triggersWithType.length})
                    .then(triggers => this.triggers = triggers.results);
            },
            setBackfillModal(trigger, bool) {
                this.isBackfillOpen = bool
                this.selectedTrigger = trigger
            },
            checkBackfill() {
                if (!this.backfill.start) {
                    return true
                }
                if (this.backfill.end && this.backfill.start > this.backfill.end) {
                    return true
                }
                if (this.flow.inputs) {
                    const requiredInputs = this.flow.inputs.map(input => input.required !== false ? input.id : null).filter(i => i !== null)
                    if (requiredInputs.length > 0) {
                        if (!this.backfill.inputs) {
                            return true
                        }
                        const fillInputs = Object.keys(this.backfill.inputs).filter(i => this.backfill.inputs[i])
                        if (requiredInputs.sort().join(",") !== fillInputs.sort().join(",")) {
                            return true
                        }
                    }
                }
                if (this.backfill.labels.length > 0) {
                    for (let label of this.backfill.labels) {
                        if ((label.key && !label.value) || (!label.key && label.value)) {
                            return true
                        }
                    }
                }
                return false
            },
            postBackfill() {
                this.$store.dispatch("trigger/update", {
                    ...this.selectedTrigger,
                    backfill: this.cleanBackfill
                })
                    .then(newTrigger => {
                        this.$toast().saved(newTrigger.id);
                        this.triggers = this.triggers.map(t => {
                            if (t.id === newTrigger.id) {
                                return newTrigger
                            }
                            return t
                        })
                        this.setBackfillModal(null, false);
                        this.backfill = {
                            start: null,
                            end: null,
                            inputs: null,
                            labels: []
                        }
                    })

            },
            pauseBackfill(trigger) {
                this.$store.dispatch("trigger/pauseBackfill", trigger)
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
                this.$store.dispatch("trigger/unpauseBackfill", trigger)
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
                this.$store.dispatch("trigger/deleteBackfill", trigger)
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
                this.$store.dispatch("trigger/update", {...trigger, disabled: !value})
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
                this.$store.dispatch("trigger/unlock", {
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
            backfillProgression(backfill) {
                const startMoment = moment(backfill.start);
                const endMoment = moment(backfill.end);
                const currentMoment = moment(backfill.currentDate);

                const totalDuration = endMoment.diff(startMoment);
                const elapsedDuration = currentMoment.diff(startMoment);
                return Math.round((elapsedDuration / totalDuration) * 100);
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