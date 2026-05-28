<template>
    <div v-if="row?.backfill" class="backfill-banner">
        <span class="bf-meta">
            {{ formatRange(row.backfill) }} · {{ progress }}%
        </span>
        <KsProgress
            class="bf-progress"
            :percentage="progress"
            :status="row.backfill.paused ? 'warning' : ''"
            :stroke-width="10"
            :showText="false"
            :striped="!row.backfill.paused"
            stripedFlow
        />
        <div class="bf-actions">
            <KsIconButton
                v-if="!row.backfill.paused"
                data-test="backfill-pause"
                size="small"
                :tooltip="t('pause backfill')"
                @click="emit('pause')"
            >
                <Pause />
            </KsIconButton>
            <KsIconButton
                v-else
                data-test="backfill-resume"
                size="small"
                :tooltip="t('continue backfill')"
                @click="emit('resume')"
            >
                <Play />
            </KsIconButton>
            <KsIconButton
                data-test="backfill-stop"
                size="small"
                :tooltip="t('delete backfill')"
                class="bf-stop"
                @click="emit('stop')"
            >
                <Stop />
            </KsIconButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import moment from "moment"
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"

    import Play from "vue-material-design-icons/Play.vue"
    import Pause from "vue-material-design-icons/Pause.vue"
    import Stop from "vue-material-design-icons/Stop.vue"

    import {dateUtils, KsIconButton, KsProgress} from "@kestra-io/design-system"

    const {t} = useI18n()

    const props = defineProps<{
        row: {
            backfill?: {
                start?: string
                end?: string
                currentDate?: string
                paused?: boolean
            }
        }
    }>()

    const emit = defineEmits<{
        pause: []
        resume: []
        stop: []
    }>()

    const progress = computed(() => {
        const bf = props.row?.backfill
        if (!bf?.start || !bf?.end || !bf?.currentDate) return 0
        const total = moment(bf.end).diff(moment(bf.start))
        if (total <= 0) return 100
        const elapsed = moment(bf.currentDate).diff(moment(bf.start))
        return Math.max(0, Math.min(100, Math.round((elapsed / total) * 100)))
    })

    const formatRange = (bf: NonNullable<typeof props.row.backfill>) => {
        const fmt = (s?: string) => s ? dateUtils.dateFilter(s) : "—"
        return `${fmt(bf.start)} → ${fmt(bf.end)}`
    }
</script>

<style lang="scss" scoped>
.backfill-banner {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.625rem 1rem;
    background: var(--ks-background-card);
    border-top: 1px dashed var(--ks-border-primary);
}

.bf-meta {
    color: var(--ks-content-secondary);
    font-size: var(--ks-font-size-sm);
    min-width: 14rem;
    white-space: nowrap;
}

.bf-progress {
    flex: 1;
    min-width: 0;
}

.bf-actions {
    display: flex;
    gap: 0.25rem;
}

.bf-stop :deep(.material-design-icon) {
    color: var(--ks-content-error);
}
</style>
