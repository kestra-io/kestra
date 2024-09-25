<template>
    <div class="input-group">
        <label for="years">Years</label>
        <el-input-number
            size="small"
            controls-position="right"
            id="years"
            v-model="years"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="months">Months</label>
        <el-input-number
            size="small"
            controls-position="right"
            id="months"
            v-model="months"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="weeks">Weeks</label>
        <el-input-number
            size="small"
            controls-position="right"
            id="weeks"
            v-model="weeks"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="days">Days</label>
        <el-input-number
            size="small"
            controls-position="right"
            id="days"
            v-model="days"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="hours">Hours</label>
        <el-input-number
            size="small"
            controls-position="right"
            id="hours"
            v-model="hours"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="minutes">Minutes</label>
        <el-input-number
            size="small"
            controls-position="right"
            id="minutes"
            v-model="minutes"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="seconds">Seconds</label>
        <el-input-number
            size="small"
            controls-position="right"
            id="seconds"
            v-model="seconds"
            :min="0"
        />
    </div>
    <div>
        <el-text size="small" :type="this.durationIssue ? 'danger': ''">
            {{ this.durationIssue ?? "or input custom duration:" }}
        </el-text>
        <el-input type="text" id="customDuration" v-model="customDuration" @input="parseDuration" placeholder="Custom duration" />
    </div>
</template>

<script>
    import {Duration, Period} from "@js-joda/core";
    export default {
        props: {
            modelValue: {
                type: String,
                default: ""
            }
        },
        emits: ["update:model-value"],
        mounted() {
            this.parseDuration(this.modelValue);
            this.updateDuration();
        },
        updated() {
            if (this.modelValue) {
                this.parseDuration(this.modelValue);
                this.updateDuration();
            }
        },
        data() {
            return {
                years: 0,
                months: 0,
                weeks: 0,
                days: 0,
                hours: 0,
                minutes: 0,
                seconds: 0,
                customDuration: "",
                durationIssue: null
            };
        },
        watch: {
            years: "updateDuration",
            months: "updateDuration",
            weeks: "updateDuration",
            days: "updateDuration",
            hours: "updateDuration",
            minutes: "updateDuration",
            seconds: "updateDuration"
        },
        methods: {
            updateDuration() {
                let duration = "P"
                if (this.years > 0) {
                    duration += `${this.years}Y`;
                }
                if (this.months > 0) {
                    duration += `${this.months}M`;
                }
                if (this.weeks > 0) {
                    duration += `${this.weeks}W`;
                }
                if (this.days > 0) {
                    duration += `${this.days}D`;
                }
                if (this.hours > 0 || this.minutes > 0 || this.seconds > 0) {
                    duration += "T"
                    if (this.hours > 0) {
                        duration += `${this.hours}H`;
                    }
                    if (this.minutes > 0) {
                        duration += `${this.minutes}M`;
                    }
                    if (this.seconds > 0) {
                        duration += `${this.seconds}S`;
                    }
                }

                if (duration === "P") {
                    duration = null;
                }

                this.customDuration = duration;
                this.durationIssue = null;
                this.$emit("update:model-value", duration);
            },
            parseDuration(durationString) {
                this.customDuration = durationString;
                const [datePart, timePart] = durationString.includes("T") ? durationString.split("T") : [durationString, null];
                let durationIssueMessage = null;

                try {
                    if (datePart && datePart !== "P") {
                        const period = Period.parse(datePart);
                        this.years = period.years();
                        this.months = period.months();
                        const days = period.days();

                        this.weeks = Math.floor(days / 7);
                        this.days = days % 7;
                    } else {
                        this.years = 0; this.months = 0; this.weeks = 0; this.days = 0;
                    }

                    if (timePart) {
                        const timeDuration = Duration.parse(`PT${timePart}`);
                        this.hours = timeDuration.toHours();
                        this.minutes = timeDuration.toMinutes() % 60;
                        this.seconds = timeDuration.seconds() % 60;
                    } else {
                        this.hours = 0; this.minutes = 0; this.seconds = 0;
                    }

                } catch (e) {
                    durationIssueMessage = e.message;
                    this.$emit("update:model-value", null);
                }

                this.durationIssue = durationIssueMessage;
            }
        }
    };
</script>

<style scoped>
    .input-group {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 80px;
        margin-left: 0.5rem;
    }
</style>