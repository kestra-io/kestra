<template>
    <b-button-group>
        <b-button @click="toggleAutoRefresh" :pressed="autoRefresh">
            <kicon :tooltip="$t('toggle periodic refresh each 10 seconds')" placement="bottomleft">
                <clock />
            </kicon>
        </b-button>
        <b-button @click="triggerRefresh">
            <kicon :tooltip="$t('trigger refresh')" placement="bottomleft">
                <refresh />
            </kicon>
        </b-button>
    </b-button-group>
</template>
<script>
    import Refresh from "vue-material-design-icons/Refresh";
    import Clock from "vue-material-design-icons/Clock";
    import Kicon from "../Kicon"
    export default {
        components: {Refresh, Clock, Kicon},
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
