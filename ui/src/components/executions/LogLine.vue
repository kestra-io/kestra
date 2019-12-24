<template>
    <div class="line" v-if="filtered">
        <span :class="levelClass" class="badge">{{log.level.padEnd(9)}}</span> <span
            class="badge bg-light text-dark">{{log
        .timestamp}}</span> <span class="message">{{log.message}}</span>
    </div>
</template>
<script>
export default {
    props: {
        log: {
            type: Object,
            required: true
        },
        filter: {
            type: String,
            default: ""
        },
        level: {
            type: String,
            default: "ALL"
        }
    },
    computed: {
        levelClass() {
            return {
                DEBUG: "badge-secondary",
                INFO: "badge-primary",
                WARNING: "badge-warning",
                ERROR: "badge-danger",
                CRITICAL: "badge-danger font-weight-bold"
            }[this.log.level];
        },
        filtered() {
            return (
                (this.level === "ALL" || this.log.level === this.level) &&
                this.log.message.toLowerCase().includes(this.filter)
            );
        }
    }
};
</script>
<style scoped lang="scss">
@import "../../styles/_variable.scss";

div {
    white-space: pre-wrap;
    word-break: break-all;
    border-top: 1px solid $dark;

    .badge {
        font-size: 100%;
        white-space: pre-wrap;
        font-weight: $font-weight-base;
    }

    .message {
        padding: 0 $badge-padding-x ;
    }
}
</style>