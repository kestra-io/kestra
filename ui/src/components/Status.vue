<template>
    <b-button class="status text-white rounded-lg" :class="cls">
        <component :is="icon"></component>
        {{status | lower | cap }}
    </b-button>
</template>
<script>
import PauseCircleOutline from "vue-material-design-icons/PauseCircleOutline";
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline";
import PlayCircleOutline from "vue-material-design-icons/PlayCircleOutline";
import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline";

export default {
    components: {
        PauseCircleOutline,
        CheckCircleOutline,
        PlayCircleOutline,
        CloseCircleOutline
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
            return {
                CREATED: "btn-info",
                SUCCESS: "btn-success",
                RUNNING: "btn-warning",
                FAILED: "btn-danger"
            }[this.status] + (this.size ? " btn-" + this.size : "");
        },
        icon () {
            return {
                CREATED: "pause-circle-outline",
                SUCCESS: 'check-circle-outline',
                RUNNING: 'play-circle-outline',
                FAILED: 'close-circle-outline'
            }[this.status];
        }
    }
};
</script>
<style scoped>
button.status {
    cursor: default;
    white-space: nowrap;
}
</style>