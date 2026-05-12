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

<script>
    import {State, durationUtils} from "@kestra-io/design-system"

    const ts = date => new Date(date).getTime()

    export default {
        props: {
            histories: {
                type: Array,
                default: undefined,
            },
        },
        watch: {
            histories(newValue, oldValue) {
                if (oldValue.length !== newValue.length) {
                    this.paint()
                }
            },
        },
        data () {
            return {
                duration: "",
                refreshHandler: undefined,
            }
        },
        mounted() {
            this.paint()
        },
        computed: {
            start() {
                return this.histories && this.histories.length && ts(this.histories[0].date)
            },

            lastStep() {
                return this.histories[this.histories.length - 1]
            },
        },
        methods: {
            paint() {
                if (!this.refreshHandler) {
                    this.refreshHandler = setInterval(() => {
                        this.computeDuration()
                        if (this.histories && !State.isRunning(this.lastStep.state)) {
                            this.cancel()
                        }
                    }, 100)
                }
            },
            cancel() {
                if (this.refreshHandler) {
                    clearInterval(this.refreshHandler)
                    this.refreshHandler = undefined
                }
            },
            delta() {
                return this.stop() - this.start
            },
            stop() {
                if (!this.histories || State.isRunning(this.lastStep.state)) {
                    return +new Date()
                }
                return ts(this.lastStep.date)
            },
            computeDuration() {
                this.duration = durationUtils.humanDuration(this.delta() / 1000)
            },
            squareClass(state) {
                let statusVarname = state.toLowerCase()

                // Minor hack to reuse created color for submitted status.
                // See https://github.com/kestra-io/kestra/issues/14876 for more details.
                if(statusVarname === "submitted") statusVarname = "created"

                return {
                    backgroundColor: `var(--ks-status-${statusVarname})`,
                }
            },
        },
        beforeUnmount() {
            this.cancel()
        },
    }
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
