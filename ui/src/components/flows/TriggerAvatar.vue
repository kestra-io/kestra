<template>
    <div>
        <span v-for="trigger in triggers" :key="uid(trigger)">
            <b-avatar
                size="sm"
                variant="primary"
                :text="name(trigger)"
                button
                @click="showTriggerDetails(trigger)"
            />
        </span>
    </div>
</template>
<script>

    export default {
        props: {
            flow: {
                type: Object,
                default: () => undefined,
            },
            execution: {
                type: Object,
                default: () => undefined,
            },
        },
        components: {},
        methods: {
            showTriggerDetails(trigger) {
                this.$emit("showTriggerDetails", trigger);
            },
            uid(trigger) {
                return (this.flow ? this.flow.namespace + "-" + this.flow.id : this.execution.namespace + "-" + this.execution.flowId) + "-" + trigger.id
            },
            name(trigger) {
                let split = trigger.id.split(".");

                return split[split.length - 1].substr(0, 1).toUpperCase();
            },
        },
        computed: {
            triggers() {
                if (this.flow && this.flow.triggers) {
                    return this.flow.triggers
                } else if (this.execution && this.execution.trigger) {
                    return [this.execution.trigger]
                } else {
                    return []
                }

            }
        }
    };
</script>

<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.b-avatar {
    margin-right: $badge-pill-padding-x / 2;
}

</style>
