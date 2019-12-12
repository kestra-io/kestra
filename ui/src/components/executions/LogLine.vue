<template>
    <div v-if="filtered" class="message-wrapper">
        <span class="level" :class="levelClass">{{log.level}}</span>
        <span class="date text-secondary">{{log.timestamp}}</span>
        <span class="message">{{log.message}}</span>
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
            default: 'ALL'
        }
    },
    computed: {
        levelClass() {
            return {
                DEBUG: "bg-secondary text-white",
                INFO: "bg-primary text-white",
                WARNING: "bg-warning text-white",
                ERROR: "bg-danger text-white",
                CRITICAL: "bg-danger text-white font-weight-bold"
            }[this.log.level];
        },
        filtered () {
            return (this.level === 'ALL' || this.log.level === this.level) && this.log.message.toLowerCase().includes(this.filter)
        }
    }
};
</script>
<style scoped lang="scss">
.message-wrapper {
    margin-bottom: 3px;
}
.level {
    padding-right: 5px;
    padding-left: 5px;
    padding-top: 2px;
    padding-bottom: 2px;
    border-radius: 3px;
    margin-right: 5px;
}
.date {
    margin-right: 15px;
}
</style>