<template>
    <el-button-group size="small">
        <ValidationError :error="flowError" />
        <el-tooltip :content="$t('source')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType('source')" @click="switchView('source')" :icon="FileDocumentEdit" />
        </el-tooltip>
        <el-tooltip :content="$t('source and doc')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType('source-doc')" @click="switchView('source-doc')" :icon="BookOpenPageVariantOutline" />
        </el-tooltip>
        <el-tooltip :content="$t('source and topology')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType('source-topology')" @click="switchView('source-topology')" :icon="FileChart" />
        </el-tooltip>
        <el-tooltip :content="$t('topology')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType('topology')" @click="switchView('topology')" :icon="Graph" />
        </el-tooltip>
        <el-tooltip :content="$t('source and blueprints')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType('source-blueprints')" @click="switchView('source-blueprints')" :icon="ImageSearch" />
        </el-tooltip>
    </el-button-group>
</template>

<script setup>
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";
    import Graph from "vue-material-design-icons/Graph.vue";
    import ImageSearch from "vue-material-design-icons/ImageSearch.vue";
    import FileChart from "vue-material-design-icons/FileChart.vue";
    import BookOpenPageVariantOutline from "vue-material-design-icons/BookOpenPageVariantOutline.vue";
</script>

<script>
    import {mapGetters} from "vuex";
    import ValidationError from "../flows/ValidationError.vue";

    export default {

        components: {ValidationError},
        props: {
            type: {
                type: String
            }
        },
        computed: {
            ...mapGetters("flow", ["flowError"]),
        },
        methods: {
            switchView(view) {
                this.$emit("switch-view", view)
            },
            buttonType(view) {
                return view === this.type ? "primary" : "default";
            }
        }
    }
</script>
