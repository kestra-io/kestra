<template>
    <div class="text-center" v-if="flow.triggers && flow.triggers.length">
        <b-badge pill variant="info" :id="`popover-target-${flow.id}`">
            <lightning-bolt />
        </b-badge>
        <b-popover
            :target="`popover-target-${flow.id}`"
            triggers="hover"
            placement="left"
        >
            <h5>{{ $t("triggers") }}</h5>
            <b-list-group>
                <b-list-group-item
                    class="pointer"
                    @click="showTriggerDetails({ trigger, flow: flow })"
                    v-for="trigger in flow.triggers"
                    :key="trigger.id"
                >
                    <dots-vertical /> {{ trigger.id }}
                </b-list-group-item>
            </b-list-group>
            <hr />
        </b-popover>
    </div>
</template>
<script>
import DotsVertical from "vue-material-design-icons/DotsVertical";
import LightningBolt from "vue-material-design-icons/LightningBolt";
export default {
    props: {
        flow: {
            type: Object,
            required: true,
        },
    },
    components: { DotsVertical, LightningBolt },
    methods: {
        showTriggerDetails(details) {
            this.$emit("showTriggerDetails", details);
        },
    },
};
</script>