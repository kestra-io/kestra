<template>
    <b-modal
        size="lg"
        id="modal-triggers-details"
        :title="`${$t('trigger details')}: ${trigger ? trigger.id : ''}`"
        hide-footer
    >
        <div v-if="trigger">
            <vars :data="triggerData" />
        </div>
    </b-modal>
</template>
<script>
import Vars from "../executions/Vars";
import Markdown from "../../utils/markdown";

export default {
    components: { Vars },
    props: {
        trigger: {
            type: Object,
            default: () => undefined
        },
    },
    computed: {
        triggerData() {
            if (this.trigger.description) {
                return {...this.trigger, description: Markdown.render(this.trigger.description)}
            }

            return this.trigger
        }
    }
};
</script>
