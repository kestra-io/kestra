<template>
    <b-modal
        size="lg"
        id="modal-triggers-details"
        :title="`${$t('trigger details')}: ${trigger ? trigger.id : ''}`"
        hide-footer
    >
        <div v-if="trigger">
            <vars :data="trigger" />
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
            required: true
        },
    },
};
</script>
