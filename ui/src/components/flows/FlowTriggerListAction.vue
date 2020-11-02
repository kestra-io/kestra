<template>
    <div v-if="flow.triggers && flow.triggers.length">
        <span v-for="trigger in flow.triggers" :key="uid(trigger)">
            <b-avatar
                size="sm"
                variant="primary"
                :text="name(trigger)"
                button
                @click="showTriggerDetails({ trigger, flow: flow })"
                >
            </b-avatar>
        </span>
    </div>
</template>
<script>

export default {
    props: {
        flow: {
            type: Object,
            required: true,
        },
    },
    components: {},
    methods: {
        showTriggerDetails(details) {
            this.$emit("showTriggerDetails", details);
        },
        uid(trigger) {
            return this.flow.namespace + "-" + this.flow.id + '-' + trigger.id
        },
        name(trigger) {
            let split = trigger.id.split(".");

            return split[split.length - 1].substr(0, 1).toUpperCase();
        },
    },
};
</script>

<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.b-avatar {
    margin-right: $badge-pill-padding-x / 2;
}

</style>
