<template>
    <span>
        <el-tooltip v-if="histories" popper-class="duration-tt" :persistent="false" transition="" :hide-after="0">
            <template #content>
                <span v-for="(history, index) in histories" :key="'tt-' + index">
                    <span class="square" :class="squareClass(history.state)" />
                    <strong>{{ history.state }}:</strong> {{ $filters.date(history.date, 'iso') }} <br>
                </span>
            </template>

            <span>{{ duration }}</span>
        </el-tooltip>
    </span>
</template>

<script>
    import State from "../../utils/state";
    import Utils from "../../utils/utils";

    const ts = date => new Date(date).getTime();

    export default {
        props: {
            histories: {
                type: Array,
                default: undefined
            }
        },
        watch: {
            histories(oldValue, newValue) {
                if (oldValue[0].date !== newValue[0].date) {
                    this.paint()
                }
            },
        },
        data () {
            return {
                duration: "",
                enabled: true
            }
        },
        mounted() {
            this.enabled = true
            this.paint()
        },
        computed: {
            start() {
                return this.histories && this.histories.length && ts(this.histories[0].date);
            },

            lastStep() {
                return this.histories[this.histories.length - 1]
            }
        },
        methods: {
            paint() {
                const repaint = () => {
                    this.computeDuration()
                    if (this.enabled && this.histories && State.isRunning(this.lastStep.state)) {
                        setTimeout(repaint, 100);
                    }
                }
                setTimeout(repaint);
            },
            delta() {
                return this.stop() - this.start;
            },
            stop() {
                if (!this.histories || State.isRunning(this.lastStep.state)) {
                    return +new Date();
                }
                return ts(this.lastStep.date)
            },
            computeDuration() {
                this.duration = Utils.humanDuration(this.delta() / 1000)
            },
            squareClass(state) {
                return [
                    "bg-" + State.colorClass()[state]
                ]
            }
        },
        unmounted() {
            this.enabled = false
        }
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
