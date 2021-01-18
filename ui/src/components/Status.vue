<template>
    <b-button class="status text-white rounded-lg" :class="cls">
        <component :is="icon" />
        <template v-if="label">
            {{ status | lower | cap }}
        </template>
    </b-button>
</template>

<script>
    import State from "../utils/state";
    import PauseCircle from "vue-material-design-icons/PauseCircle";
    import CheckCircle from "vue-material-design-icons/CheckCircle";
    import PlayCircle from "vue-material-design-icons/PlayCircle";
    import CloseCircle from "vue-material-design-icons/CloseCircle";
    import StopCircle from "vue-material-design-icons/StopCircle";
    import Restart from "vue-material-design-icons/Restart";
    import AlertCircle from "vue-material-design-icons/AlertCircle";

    export default {
        components: {
            PauseCircle,
            CheckCircle,
            PlayCircle,
            CloseCircle,
            StopCircle,
            Restart,
            AlertCircle

        },
        props: {
            status: {
                type: String,
                required: true
            },
            size: {
                type: String,
                default: ""
            },
            label: {
                type: Boolean,
                default: true
            }
        },
        computed: {
            cls() {
                return {
                    ["btn-" + State.colorClass()[this.status] + (this.size ? " btn-" + this.size : "")]: true,
                    "no-label": !this.label
                }
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

.no-label {
    line-height: 1;
}
</style>
