import DisabledTriggerRemovedDialog from "../../../../src/components/flows/DisabledTriggerRemovedDialog.vue";
import {useFlowStore} from "../../../../src/stores/flow";

export default {
    title: "Components/DisabledTriggerRemovedDialog",
    component: DisabledTriggerRemovedDialog,
};

const Template = (args) => ({
    setup() {
        const flowStore = useFlowStore();
        flowStore.removedDisabledTriggers = args.triggers;

        return () =>
            <div style="height: 100vh">
                <DisabledTriggerRemovedDialog/>
            </div>
    }
});

export const OneTrigger = Template.bind({});
OneTrigger.args = {
    triggers: ["every-morning"],
};

export const SeveralTriggers = Template.bind({});
SeveralTriggers.args = {
    triggers: ["every-morning", "on-webhook", "hourly-backfill"],
};

export const Closed = Template.bind({});
Closed.args = {
    triggers: [],
};
