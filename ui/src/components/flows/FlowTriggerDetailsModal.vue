<template>
    <b-modal
        size="lg"
        id="modal-triggers-details"
        :title="`${$t('trigger details')} : ${trigger ? trigger.flow.id : ''}`"
        hide-footer
    >
        <div v-if="trigger">
            <h5><b>Type : </b>{{ trigger.trigger.type }}</h5>
            <hr />
            <div v-if="trigger.trigger.inputs">
                <p>{{ $t("inputs") }}</p>
                <vars :execution="{}" :data="trigger.trigger.inputs" />
                <br />
            </div>
            <div v-if="trigger.trigger.conditions">
                <p>{{ $t("conditions") }}</p>
                <vars
                    v-for="(condition, x) in trigger.trigger.conditions"
                    :key="x"
                    :execution="{}"
                    :data="condition"
                />
            </div>
        </div>
    </b-modal>
</template>
<script>
import Vars from "../executions/Vars";

export default {
    components: { Vars },
    props: {
        trigger: {
            type: Object,
            default: () => undefined,
        },
    },
};
</script>