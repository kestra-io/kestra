<template>
    <b-button-group size="sm">
        <b-tooltip
            target="toggle-automatic-refresh-action"
        >{{$t('toggle periodic refresh each 10 seconds')}}</b-tooltip>
        <b-button @click="toggleAutoRefresh" :pressed="autoRefresh" id="toggle-automatic-refresh-action">
            <clock/> <span class="label">{{$t('automatic refresh')}}</span>
        </b-button>
        <b-tooltip target="trigger-refresh-action">{{ $t('trigger refresh') }}</b-tooltip>
        <b-button @click="triggerRefresh" id="trigger-refresh-action">
            <refresh/> <span class="label">{{ $t('trigger refresh') }}</span>
        </b-button>
    </b-button-group>
</template>
<script>
import Refresh from "vue-material-design-icons/Refresh";
import Clock from "vue-material-design-icons/Clock";

export default {
    components: { Refresh, Clock },
    data() {
        return {
            autoRefresh: false,
            refreshHandler: undefined
        };
    },
    created() {
        this.autoRefresh = localStorage.getItem("autoRefresh") === "1";
    },
    methods: {
        toggleAutoRefresh() {
            this.autoRefresh = !this.autoRefresh;
            localStorage.setItem("autoRefresh", this.autoRefresh ? "1" : "0");
            if (this.autoRefresh) {
                this.refreshHandler = setInterval(this.triggerRefresh, 10000);
                this.triggerRefresh()
            } else {
                this.stopRefresh();
            }
        },
        triggerRefresh() {
            this.$emit("onRefresh");
        },
        stopRefresh() {
            if (this.refreshHandler) {
                clearInterval(this.refreshHandler);
                this.refreshHandler = undefined
            }
        }
    },
    beforeDestroy() {
        this.stopRefresh();
    }
};
</script>

<style lang="scss">
@import "../../styles/variable";

.btn-group {
    .btn {
        span.label {
            display: none;
        }
    }
}
.navbar-collapse {
    &.collapse.show {
        .btn-group {
            width: 100%;
            display: flex !important;
            .btn {
                width: auto;
                flex: 1;

                @include media-breakpoint-up(sm) {
                    span.label {
                        display: inline-block;
                    }

                }

            }
        }
    }
}


</style>
