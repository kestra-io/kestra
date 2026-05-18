<template>
    <span>
        <KsTooltip v-if="histories" popperClass="duration-tt">
            <template #content>
                <span v-for="(history, index) in histories" :key="'tt-' + index">
                    <span class="square" :style="squareClass(history.state)" />
                    <strong>{{ history.state }}:</strong> {{ date(history.date, 'iso') }} <br>
                </span>
            </template>

            <span>{{ duration }}</span>
        </KsTooltip>
    </span>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, onBeforeUnmount} from "vue"
    import {State, durationUtils} from "@kestra-io/design-system"
    import {date} from "../../utils/filters"

    const ts = (d: string) => new Date(d).getTime()

    const props = defineProps<{
        histories?: Array<{ state: string; date: string }>
    }>()

    const duration = ref("")
    const refreshHandler = ref<ReturnType<typeof setInterval> | undefined>(undefined)

    const start = computed<number | false>(() =>
        !!(props.histories && props.histories.length) && ts(props.histories[0].date),
    )

    const lastStep = computed(() =>
        props.histories![props.histories!.length - 1],
    )

    function stop(): number {
        if (!props.histories || State.isRunning(lastStep.value.state)) {
            return +new Date()
        }
        return ts(lastStep.value.date)
    }

    function delta(): number {
        return stop() - (start.value as number)
    }

    function computeDuration() {
        duration.value = durationUtils.humanDuration(delta() / 1000)
    }

    function cancel() {
        if (refreshHandler.value) {
            clearInterval(refreshHandler.value)
            refreshHandler.value = undefined
        }
    }

    function paint() {
        if (!refreshHandler.value) {
            refreshHandler.value = setInterval(() => {
                computeDuration()
                if (props.histories && !State.isRunning(lastStep.value.state)) {
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
            backgroundColor: `var(--ks-chart-${statusVarname})`,
        }
    }

    watch(() => props.histories, (newValue, oldValue) => {
        if (oldValue?.length !== newValue?.length) {
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
