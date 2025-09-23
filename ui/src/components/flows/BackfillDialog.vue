<template>
    <el-dialog v-model="internalOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="$t('backfill executions')" />
        </template>
        <el-form :model="backfill" labelPosition="top">
            <div class="pickers">
                <div class="small-picker">
                    <el-form-item label="Start">
                        <el-date-picker
                            v-model="backfill.start"
                            type="datetime"
                            placeholder="Start"
                            :disabledDate="time => new Date() < time || backfill.end ? time > backfill.end : false"
                        />
                    </el-form-item>
                </div>
                <div class="small-picker">
                    <el-form-item label="End">
                        <el-date-picker
                            v-model="backfill.end"
                            type="datetime"
                            placeholder="End"
                            :disabledDate="time => new Date() < time || backfill?.start > time"
                        />
                    </el-form-item>
                </div>
            </div>
        </el-form>
        <FlowRun
            @update-inputs="backfill.inputs = $event"
            @update-labels="backfill.labels = $event"
            :selectedTrigger="trigger"
            :redirect="false"
            :embed="true"
        />
        <template #footer>
            <router-link
                v-if="isSchedule(trigger.type) && backfillRouteName"
                :to="{name: backfillRouteName, query:{namespace, flowId, q: trigger.triggerId}}"
            >
                <el-button class="me-2">
                    {{ $t('backfill') }}
                </el-button>
            </router-link>
            <el-button
                type="primary"
                @click="postBackfill()"
                :disabled="checkBackfill"
            >
                {{ $t("execute backfill") }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import FlowRun from "./FlowRun.vue";
</script>

<script lang="ts">
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
    import {useTriggerStore} from "../../stores/trigger";

    export default {
        name: "BackfillDialog",
        props: {
            modelValue: {type: Boolean, default: false},
            namespace: {type: String, required: true},
            flowId: {type: String, required: true},
            trigger: {type: Object, required: true},
            backfillRouteName: {type: String, default: "admin/triggers"}
        },
        emits: ["update:modelValue", "updated"],
        data() {
            return {
                backfill: {start: null, end: null, inputs: null, labels: []},
                internalOpen: this.modelValue
            }
        },
        computed: {
            ...mapStores(useExecutionsStore, useTriggerStore),
            checkBackfill() {
                if (!this.backfill.start) return true;
                if (this.backfill.end && this.backfill.start > this.backfill.end) return true;
                const flow = this.executionsStore.flow;
                if (flow?.inputs?.length) {
                    const requiredInputs = flow.inputs.map(input => input.required !== false ? input.id : null).filter(i => i !== null);
                    if (requiredInputs.length > 0) {
                        if (!this.backfill.inputs) return true;
                        const fillInputs = Object.keys(this.backfill.inputs).filter(i => this.backfill.inputs[i] !== null && this.backfill.inputs[i] !== undefined);
                        if (requiredInputs.sort().join(",") !== fillInputs.sort().join(",")) return true;
                    }
                }
                if (this.backfill.labels.length > 0) {
                    for (let label of this.backfill.labels) {
                        if ((label.key && !label.value) || (!label.key && label.value)) return true;
                    }
                }
                return false;
            }
        },
        watch: {
            modelValue: {
                immediate: true,
                handler(val) {
                    this.internalOpen = val;
                    if (val) this.ensureFlowLoaded();
                }
            },
            internalOpen(val) {
                this.$emit("update:modelValue", val);
            },
            namespace() {
                if (this.internalOpen) this.ensureFlowLoaded();
            },
            flowId() {
                if (this.internalOpen) this.ensureFlowLoaded();
            },
            "executionsStore.flow": {handler() {}, deep: false}
        },
        methods: {
            async ensureFlowLoaded() {
                await this.executionsStore.loadFlowForExecution({
                    namespace: this.namespace,
                    flowId: this.flowId,
                    store: true
                });
            },
            isSchedule(type) {
                return type === "io.kestra.plugin.core.trigger.Schedule" || type === "io.kestra.core.models.triggers.types.Schedule";
            },
            async postBackfill() {
                const cleanBackfill = {
                    ...this.backfill,
                    labels: this.backfill.labels.filter(label => label.key && label.value)
                };
                const updated = await this.triggerStore.update({
                    ...this.trigger,
                    backfill: cleanBackfill
                });
                this.$toast().saved(updated.id);
                this.$emit("updated", updated);
                this.internalOpen = false;
                this.backfill = {start: null, end: null, inputs: null, labels: []};
            }
        }
    }
</script>

<style scoped>
    .pickers {
        display: flex;
        justify-content: space-between;

        .small-picker { width: 49%; }
    }
</style>


