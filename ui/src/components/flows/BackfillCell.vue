<template>
    <div class="backfill-cell">
        <el-button
            :icon="CalendarCollapseHorizontalOutline"
            v-if="isSchedule(trigger.type) && !trigger.backfill && canCreate"
            @click="$emit('open-backfill', trigger)"
            :disabled="trigger.disabled || trigger.sourceDisabled || trigger.codeDisabled"
            size="small"
            type="primary"
        >
            {{ $t("backfill executions") }}
        </el-button>

        <template v-else-if="isSchedule(trigger.type) && canUpdate && trigger.backfill">
            <div class="progress-cell">
                <el-progress
                    :percentage="backfillProgression(trigger.backfill)"
                    :status="trigger.backfill.paused ? 'warning' : ''"
                    :stroke-width="12"
                    :showText="!trigger.backfill.paused"
                    :striped="!trigger.backfill.paused"
                    stripedFlow
                />
            </div>
            <template v-if="!trigger.backfill.paused">
                <el-button size="small" @click="pauseBackfill(trigger)">
                    <Kicon :tooltip="$t('pause backfill')">
                        <Pause />
                    </Kicon>
                </el-button>
            </template>
            <template v-else>
                <el-button size="small" @click="unpauseBackfill(trigger)">
                    <Kicon :tooltip="$t('continue backfill')">
                        <Play />
                    </Kicon>
                </el-button>
                <el-button size="small" @click="deleteBackfill(trigger)">
                    <Kicon :tooltip="$t('delete backfill')">
                        <Delete />
                    </Kicon>
                </el-button>
            </template>
        </template>
    </div>
</template>

<script lang="ts" setup>
    import Pause from "vue-material-design-icons/Pause.vue";
    import Play from "vue-material-design-icons/Play.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import CalendarCollapseHorizontalOutline from "vue-material-design-icons/CalendarCollapseHorizontalOutline.vue";
    import Kicon from "../Kicon.vue";
    import moment from "moment";
    import {useTriggerStore} from "../../stores/trigger";

    type Backfill = { start: string; end: string; currentDate: string; paused?: boolean } | any;

    defineProps<{
        trigger: any,
        canCreate?: boolean,
        canUpdate?: boolean
    }>();

    const emit = defineEmits(["open-backfill", "updated"]);

    const triggerStore = useTriggerStore();

    function backfillProgression(backfill: Backfill) {
        const startMoment = moment(backfill.start);
        const endMoment = moment(backfill.end);
        const currentMoment = moment(backfill.currentDate);
        const totalDuration = endMoment.diff(startMoment);
        const elapsedDuration = currentMoment.diff(startMoment);
        return Math.round((elapsedDuration / totalDuration) * 100);
    }

    function isSchedule(type: string) {
        return type === "io.kestra.plugin.core.trigger.Schedule" || type === "io.kestra.core.models.triggers.types.Schedule";
    }

    function pauseBackfill(trigger: any) {
        triggerStore.pauseBackfill(trigger).then(newTrigger => emit("updated", newTrigger));
    }

    function unpauseBackfill(trigger: any) {
        triggerStore.unpauseBackfill(trigger).then(newTrigger => emit("updated", newTrigger));
    }

    function deleteBackfill(trigger: any) {
        triggerStore.deleteBackfill(trigger).then(newTrigger => emit("updated", newTrigger));
    }
</script>

<style scoped>
    .backfill-cell { display: flex; align-items: center; }
    .progress-cell { width: 200px; margin-right: 1em; }
</style>