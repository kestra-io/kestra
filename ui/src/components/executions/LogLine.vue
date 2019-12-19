<template>
    <div v-if="filtered" class="message-wrapper d-flex">
        <div class="level text-center h-100" :class="levelClass">{{log.level}}</div>
        <div class="date text-nowrap text-light text-center">{{log.timestamp}}</div>
        <div
            class="flex-grow-1 message"
        ><pre>{{log.message}}</pre></div>
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
                DEBUG: "bg-secondary",
                INFO: "bg-primary",
                WARNING: "bg-warning",
                ERROR: "bg-danger",
                CRITICAL: "bg-danger font-weight-bold"
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
.message-wrapper {
    margin-bottom: 3px;
    > div {
        padding-right: 10px;
        padding-left: 10px;
    }
}
.level {
    width: 150px;
    padding-right: 5px;
    padding-left: 5px;
    padding-top: 2px;
    padding-bottom: 2px;
    border-radius: 3px;
    flex-shrink: 0;
    flex-basis: 8em;
}
</style>