<template>
    <div class="line text-monospace" v-if="filtered">
        <b-row>
            <b-col md="12">
                <div class="metas-wrapper">
                    <div class="float-left meta-wrapper">
                        <span :class="levelClass" class="badge">{{
                            log.level.padEnd(9)
                        }}</span>
                        <span class="badge bg-light text-dark">{{
                            log.timestamp | date("LLLL")
                        }}</span>
                    </div>
                    <div
                        class="float-left meta-wrapper"
                        v-for="(meta, x) in metaWithValue"
                        :key="x"
                    >
                        <span :class="levelClass" class="badge">{{
                            meta.key
                        }}</span>
                        <span class="badge bg-light text-dark">{{
                            meta.value
                        }}</span>
                    </div>
                </div>
            </b-col>
        </b-row>
        <span class="message">{{ log.message }}</span>
    </div>
</template>
<script>
export default {
    props: {
        log: {
            type: Object,
            required: true,
        },
        filter: {
            type: String,
            default: "",
        },
        level: {
            type: String,
        },
        metas: {
            type: Array,
            default: () => [],
        },
    },
    computed: {
        metaWithValue() {
            const metaWithValue = [];
            for (const key of this.metas) {
                if (this.log[key]) {
                    metaWithValue.push({ key, value: this.log[key] });
                }
            }
            return metaWithValue;
        },
        levelClass() {
            return {
                TRACE: "badge-info",
                DEBUG: "badge-secondary",
                INFO: "badge-primary",
                WARN: "badge-warning",
                ERROR: "badge-danger",
                CRITICAL: "badge-danger font-weight-bold",
            }[this.log.level];
        },
        filtered() {
            return (
                this.log.message &&
                this.log.message.toLowerCase().includes(this.filter)
            );
        },
    },
};
</script>
<style scoped lang="scss">
@import "../../styles/_variable.scss";

div {
    white-space: pre-wrap;
    word-break: break-all;
    padding: 0 $spacer/2;

    .badge {
        font-size: 100%;
        margin-left: -$spacer/2;
        white-space: pre-wrap;
        font-weight: $font-weight-base;
    }

    .message {
        padding: 0 $badge-padding-x;
    }
    .metas-wrapper {
        padding-left: 0px;
    }
    .text-monospace {
        padding-left: 0px;
    }
    .meta-wrapper {
        padding-right: 0px;
    }
}
</style>
