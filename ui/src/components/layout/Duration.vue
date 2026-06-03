<template>
    <span>
        <KsTooltip v-if="histories" popperClass="duration-tt">
            <template #content>
                <span v-for="(history, index) in histories" :key="'tt-' + index">
                    <span class="square" :style="squareClass(history.state)" />
                    <strong>{{ history.state }}:</strong> {{ $filters.date(history.date, 'iso') }} <br>
                </span>
            </template>

            <span>{{ duration }}</span>
        </KsTooltip>
    </span>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, onBeforeUnmount, getCurrentInstance} from "vue"
    import {State, durationUtils} from "@kestra-io/design-system"

    interface HistoryEntry {
        date: string | number
        state: string
    }

    const props = withDefaults(defineProps<{
        histories?: HistoryEntry[]
    }>(), {
        histories: undefined,
    })

    const instance = getCurrentInstance()
    // FIXME: any - $filters is a global property registered via Vue plugin
    const $filters = instance?.appContext.config.globalProperties.$filters as any // FIXME: any

    const ts = (date: string | number) => new Date(date).getTime()

    const duration = ref("")
    let refreshHandler: ReturnType<typeof setInterval> | undefined = undefined

    const start = computed(() =>
        props.histories && props.histories.length ? ts(props.histories[0].date) : 0,
    )

    const lastStep = computed(() =>
        props.histories ? props.histories[props.histories.length - 1] : undefined,
    )

    function stop(): number {
        if (!props.histories || !lastStep.value || State.isRunning(lastStep.value.state)) {
            return +new Date()
        }
        return ts(lastStep.value.date)
    }

    function delta(): number {
        return stop() - start.value
    }

    function computeDuration() {
        duration.value = durationUtils.humanDuration(delta() / 1000)
    }

    function cancel() {
        if (refreshHandler !== undefined) {
            clearInterval(refreshHandler)
            refreshHandler = undefined
        }
    }

    function paint() {
        if (!refreshHandler) {
            refreshHandler = setInterval(() => {
                computeDuration()
                if (props.histories && lastStep.value && !State.isRunning(lastStep.value.state)) {
                    cancel()
                }
            }, 100)
        }
    }

    function squareClass(state: string) {
        let statusVarname = state.toLowerCase()

        // Minor hack to reuse created color for submitted status.
        // See https://github.com/kestra-io/kestra/issues/14876 for more details.
        if (statusVarname === "submitted") statusVarname = "created"

        return {
            backgroundColor: `var(--ks-status-${statusVarname})`,
        }
    }

    watch(() => props.histories, (newValue, oldValue) => {
        if ((oldValue?.length ?? 0) !== (newValue?.length ?? 0)) {
            paint()
        }
    })

    onMounted(() => {
        paint()
    })

    onBeforeUnmount(() => {
        cancel()
    })
</script>

<style lang="scss">
.duration-tt {
    .tooltip-inner {
        text-align: left;
        white-space: nowrap;
        max-width: none;
    }

    .square {
        display: inline-block;
        width: 10px;
        height: 10px;
        margin-right: 5px;
    }
}
</style>
