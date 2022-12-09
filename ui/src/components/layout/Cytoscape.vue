<template>
    <div v-loading="!ready">
        <div class="d-flex top rounded" v-if="ready && this.count !== 0">
            <div>
                <el-button-group>
                    <slot name="btn" />
                    <el-tooltip :content="$t('topology-graph.zoom-in')" :persistent="false" transition="" :hide-after="0">
                        <el-button :icon="icon.MagnifyPlus" size="small" @click="setAction('in')" />
                    </el-tooltip>
                    <el-tooltip :content="$t('topology-graph.zoom-out')" :persistent="false" transition="" :hide-after="0">
                        <el-button :icon="icon.MagnifyMinus" size="small" @click="setAction('out')" />
                    </el-tooltip>
                    <el-tooltip :content="$t('topology-graph.zoom-reset')" :persistent="false" transition="" :hide-after="0">
                        <el-button :icon="icon.ArrowCollapseAll" size="small" @click="setAction('reset')" />
                    </el-tooltip>
                    <el-tooltip :content="$t('topology-graph.zoom-fit')" :persistent="false" transition="" :hide-after="0">
                        <el-button :icon="icon.FitToPage" size="small" @click="setAction('fit')" />
                    </el-tooltip>
                </el-button-group>
            </div>
        </div>

        <el-alert v-if="ready && this.count === 0" type="info" :closable="false" class="m-0 text-muted">
            {{ $t('no result') }}
        </el-alert>

        <div :class="{hide: !ready}" class="graph-wrapper" :id="uuid" ref="wrapper" />
    </div>
</template>

<script>
    import MagnifyPlus from "vue-material-design-icons/MagnifyPlus";
    import MagnifyMinus from "vue-material-design-icons/MagnifyMinus";
    import ArrowCollapseAll from "vue-material-design-icons/ArrowCollapseAll";
    import FitToPage from "vue-material-design-icons/FitToPage";
    import Utils from "../../utils/utils";
    import {shallowRef} from "vue";

    export default {
        data() {
            return {
                uuid: Utils.uid(),
                zoom: undefined,
                zoomFactor: 1,
                ready: false,
                count: undefined,
                icon: {
                    MagnifyPlus: shallowRef(MagnifyPlus),
                    MagnifyMinus: shallowRef(MagnifyMinus),
                    ArrowCollapseAll: shallowRef(ArrowCollapseAll),
                    FitToPage: shallowRef(FitToPage),
                },
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
.graph-wrapper {
    height: calc(100vh - 360px);
}

.top {
    background-color: var(--bs-gray-200);

    > div {
        margin: 6px;
    }
}

.hide {
    opacity: 0;
}

</style>
