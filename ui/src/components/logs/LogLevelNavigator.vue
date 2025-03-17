<template>
    <div class="d-flex el-select__wrapper space-between" :class="{border: isSelected, ['log-border-' + level.toLowerCase()]: isSelected, 'shadow-none': isSelected}">
        <div class="d-flex align-items-center gap-2">
            <span :class="'circle log-bg-' + level.toLowerCase()" />
            <span>({{ (cursorIdx === undefined ? "" : `${cursorIdx + 1} / `) + totalCount }}) {{ level }}</span>
        </div>
        <div class="d-flex align-items-center gap-2">
            <chevron-up class="medium-icon nav-button" @click="forwardEvent('previous')" />
            <chevron-down class="medium-icon nav-button" @click="forwardEvent('next')" />
            <close class="medium-icon nav-button close-button" @click="forwardEvent('close')" v-if="isSelected" />
        </div>
    </div>
</template>
<script setup>
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import Close from "vue-material-design-icons/Close.vue";
</script>
<script>
    export default {
        emits: ["previous", "next"],
        props: {
            cursorIdx: {
                type: Number,
                default: undefined
            },
            totalCount: {
                type: Number,
                required: true
            },
            level: {
                type: String,
                required: true
            }
        },
        computed: {
            isSelected() {
                return this.cursorIdx !== undefined;
            }
        },
        methods: {
            forwardEvent(eventName) {
                this.$emit(eventName);
            }
        }
    };
</script>
<style>
    .el-select__wrapper.space-between {
        justify-content: space-between;
        cursor: unset;
        white-space: nowrap;

        &:hover {
            box-shadow: 0 0 0 1px var(--ks-border-primary) inset;
        }

        .circle {
            display: inline-block;
            width: 1em;
            height: 1em;
            border-radius: 50%;
        }

        .nav-button {
            cursor: pointer;
        }

        .medium-icon {
            font-size: 1.1rem;
        }

        .close-button {
            color: var(--ks-content-link);
            &:hover {
                color: var(--ks-content-link);
            }
        }
    }
</style>
