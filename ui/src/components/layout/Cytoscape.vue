<template>
    <div>
        <b-overlay :show="!ready" variant="transparent">
            <div class="d-flex top rounded" v-if="ready && this.count !== 0">
                <div>
                    <b-btn-group>
                        <slot name="btn" />
                        <b-btn size="sm" @click="setAction('in')">
                            <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-in')">
                                <magnify-plus />
                            </kicon>
                        </b-btn>
                        <b-btn size="sm" @click="setAction('out')">
                            <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-out')">
                                <magnify-minus />
                            </kicon>
                        </b-btn>
                        <b-btn size="sm" @click="setAction('reset')" id="zoom-reset">
                            <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-reset')">
                                <arrow-collapse-all />
                            </kicon>
                        </b-btn>
                        <b-btn size="sm" @click="setAction('fit')" id="zoom-fit">
                            <kicon placement="bottom" :tooltip="$t('topology-graph.zoom-fit')">
                                <fit-to-page />
                            </kicon>
                        </b-btn>
                    </b-btn-group>
                </div>
            </div>

            <b-alert v-if="ready && this.count === 0" variant="light" class="m-0 text-muted" show>
                {{ $t('no result') }}
            </b-alert>

            <div :class="{hide: !ready}" class="graph-wrapper" :id="uuid" ref="wrapper" />
        </b-overlay>
    </div>
</template>
<script>
    import MagnifyPlus from "vue-material-design-icons/MagnifyPlus";
    import MagnifyMinus from "vue-material-design-icons/MagnifyMinus";
    import ArrowCollapseAll from "vue-material-design-icons/ArrowCollapseAll";
    import FitToPage from "vue-material-design-icons/FitToPage";
    import Utils from "../../utils/utils";
    import Kicon from "../Kicon"

    export default {
        components: {
            Kicon,
            MagnifyPlus,
            MagnifyMinus,
            ArrowCollapseAll,
            FitToPage
        },
        data() {
            return {
                uuid: Utils.uid(),
                zoom: undefined,
                zoomFactor: 1,
                ready: false,
                count: undefined,
            };
        },
        cy: undefined,

        created() {
        },
        methods: {
            instance(cy) {
                this.cy = cy;
                this.count = this.cy.nodes().length
            },
            setReady(ready) {
                this.ready = ready;
            },
            setAction(action) {
                if (action === "in") {
                    if (this.cy.zoom() <= 1.7) {
                        this.cy.zoom(this.cy.zoom() + 0.2);
                    }
                } else if (action === "out") {
                    if (this.cy.zoom() >= 0.3) {
                        this.cy.zoom(this.cy.zoom() - 0.2);
                    }
                } else if (action === "reset") {
                    this.cy.zoom(1);
                } else if (action === "fit") {
                    this.cy.fit(null, 50)
                }
            },
        },
        unmounted() {
            this.ready = false;
        }
    };
</script>
<style lang="scss" scoped>
@import "../../styles/variable";
.graph-wrapper {
    height: calc(100vh - 360px);
}

.top {
    background-color: var(--gray-200);

    > div {
        margin: 6px;
    }
}

.hide {
    opacity: 0;
}

</style>
