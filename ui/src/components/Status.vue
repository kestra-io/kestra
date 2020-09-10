<template>
    <b-button class="status text-white rounded-lg" :class="'btn-' + cls">
        <component :is="icon"></component>
        {{status | lower | cap }}
    </b-button>
</template>
<script>

import State from "../utils/state";
import PauseCircleOutline from "vue-material-design-icons/PauseCircleOutline";
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline";
import PlayCircleOutline from "vue-material-design-icons/PlayCircleOutline";
import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline";
import StopCircleOutline from "vue-material-design-icons/StopCircleOutline";

export default {
    components: {
        PauseCircleOutline,
        CheckCircleOutline,
        PlayCircleOutline,
        CloseCircleOutline,
        StopCircleOutline
    },
    props: {
        status: {
            type: String,
            required: true
        },
        size: {
            type: String,
            default: ""
        }
    },
    computed: {
        cls() {
            return State.colorClass()[this.status] + (this.size ? " btn-" + this.size : "");
        },
        icon () {
            return State.icon()[this.status];
        }
    }
};
</script>
<style scoped>
button.status {
    cursor: default !important;
    white-space: nowrap;
}
</style>
