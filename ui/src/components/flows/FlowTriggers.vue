<template>
    <el-table
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
        <el-table-column prop="type" :label="$t('type')"/>
        <el-table-column :label="$t('description')">
            <template #default="scope">
                <Markdown :source="scope.row.description"/>
            </template>
        </el-table-column>

        <el-table-column prop="nextExecutionDate" :label="$t('next execution date')"/>

        <el-table-column column-key="backfill" class-name="row-multiple-actions">
            <template #default="scope">
                <el-button
                    v-if="scheduleClassName === scope.row.type && !scope.row.backfill"
                    @click="setBackfillModal(scope.row, true)"
                    size="small"
                >
                    {{ $t("backfill executions") }}
                </el-button>
                <div v-else>
                    <div class="cell">
                        <div class="progress-cell">
                            <el-progress
                                :percentage="50"
                                :show-text="false"
                                :indeterminate="true"
                                :status="scope.row.backfill.paused ? 'warning' : 'default'"
                            />
                        </div>
                        <a href="#" v-if="!scope.row.backfill.paused">
                            <kicon :tooltip="$t('pause backfill')">
                                <Pause
                                    @click="pauseBackfill(scope.row)"
                                />
                            </kicon>
                        </a>
                        <div class="d-flex" v-else>
                            <a href="#">
                                <kicon :tooltip="$t('continue backfill')">
                                    <Play
                                        @click="unpauseBackfill(scope.row)"
                                    />
                                </kicon>
                            </a>
                            <a href="#">
                                <kicon :tooltip="$t('delete backfill')">
                                    <Delete
                                        @click="deleteBackfill(scope.row)"
                                    />
                                </kicon>
                            </a>
                        </div>
                    </div>
                </div>
            </template>
        </el-table-column>

        <el-table-column column-key="action" class-name="row-action">
            <template #default="scope">
                <a href="#" @click="triggerId = scope.row.id; isOpen = true">
                    <kicon :tooltip="$t('details')" placement="left">
                        <TextSearch/>
                    </kicon>
                </a>
            </template>
        </el-table-column>
    </el-table>

    <el-dialog v-model="isBackfillOpen" destroy-on-close :append-to-body="true" :width="470">
        <template #header>
            <span v-html="$t('backfill executions')"/>
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
            <el-form-item label="Inputs">
                <inputs-form
                    :flow-inputs="flow.inputs"
                    @update="backfill.inputs = $event"
                />
            </el-form-item>
            <el-form-item :label="$t('execution labels')">
                <label-input
                    :placeholder="$t('execution labels')"
                    v-model:labels="backfill.labels"
                />
            </el-form-item>
        </el-form>
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

    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        destroy-on-close
        lock-scroll
        size=""
        :append-to-body="true"
    >
        <template #header>
            <code>{{ triggerId }}</code>
        </template>
        <el-table stripe table-layout="auto" :data="triggerData">
            <el-table-column prop="key" :label="$t('key')"/>
            <el-table-column prop="value" :label="$t('value')">
                <template #default="scope">
                    <vars
                        v-if="scope.row.value instanceof Array || scope.row.value instanceof Object "
                        :data="scope.row.value"
                    />
                </template>
            </el-table-column>
        </el-table>
    </el-drawer>
</template>

<script setup>
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import Pause from "vue-material-design-icons/Pause.vue";
    import Play from "vue-material-design-icons/Play.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Vars from "../executions/Vars.vue";
    import LabelInput from "../labels/LabelInput.vue";
</script>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapGetters} from "vuex";
    import Kicon from "../Kicon.vue"
    import InputsForm from "../inputs/InputsForm.vue";

    export default {
        components: {Markdown, Kicon, InputsForm},
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
            ...mapGetters("flow", ["flow"]),
            triggerData() {
                return Object
                    .entries(this.triggers.filter(trigger => trigger.triggerId === this.triggerId)[0])
                    .map(([key, value]) => {
                        return {
                            key,
                            value
                        };
                    });
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
            loadData() {
                this.$store
                    .dispatch("trigger/find", {namespace: this.flow.namespace, flowId: this.flow.id})
                    .then(triggers => this.triggers = triggers.results);
            },
            setBackfillModal(trigger, bool) {
                this.isBackfillOpen = bool
                this.selectedTrigger = trigger
            },
            checkBackfill() {
                if (!this.backfill.start && !this.backfill.end) {
                    return true
                }
                if (this.backfill.start > this.backfill.end) {
                    return true
                }
                if (this.flow.inputs) {
                    const requiredInputs = this.flow.inputs.map(input => input.required !== false ? input.id : null)
                    if (requiredInputs.length > 0) {
                        if (!this.backfill.inputs) {
                            return true
                        }
                        const fillInputs = Object.keys(this.backfill.inputs)
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
                this.$store.dispatch("trigger/update",
                    {
                        ...this.selectedTrigger,
                        backfill: this.cleanBackfill
                    }).then(_ => {
                    this.loadData();
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
                    .then(_ => {
                        this.loadData();
                    })
            },
            unpauseBackfill(trigger) {
                this.$store.dispatch("trigger/unpauseBackfill", trigger)
                    .then(_ => {
                        this.loadData();
                    })
            },
            deleteBackfill(trigger) {
                this.$store.dispatch("trigger/deleteBackfill", trigger)
                    .then(_ => {
                        this.loadData();
                    })
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

    a {
        color: var(--bs-body-color);
        width: 24px;
        border-radius: var(--bs-border-radius);
        text-align: center;
        display: flex;
        justify-content: center;
        align-items: center;
        background-color: var(--bs-gray-400);

        .material-design-icon__svg {
            bottom: -0.125rem;
        }
    }

    .progress-cell {
        width: 200px;
        margin-top: 0.6em;
    }
</style>