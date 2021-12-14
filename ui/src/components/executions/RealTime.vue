<template>
    <span v-b-tooltip.hover :title="duration" v-if="histories">{{ duration }}</span>
</template>

<script>
    import humanizeDuration from "humanize-duration";
    import State from "../../utils/state";
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
            return   {
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
                const repaint = ()=> {
                    this.computeDuration()
                    if (this.enabled && this.histories && State.isRunning(this.lastStep.state)) {
                        setTimeout(repaint, 200);
                    }
                }
                setTimeout(repaint);
            },
            delta() {
                return this.stop() - this.start;
            },
            computeDuration() {
                const duration = humanizeDuration(this.delta(), {maxDecimalPoints: 0});
                if (duration.endsWith(",")) {
                    this.duration = duration.splice(duration.length - 1, 1)
                }
                this.duration = duration
            },
            stop() {
                if (!this.histories || State.isRunning(this.lastStep.state)) {
                    return +new Date();
                }
                return ts(this.lastStep.date)
            },
        },
        destroyed() {
            this.enabled = false
        }
    }
</script>
