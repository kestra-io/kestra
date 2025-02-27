<template>
    <div class="tabs">
        <el-checkbox-group v-model="activeTabs">
            <el-checkbox-button name="code" value="code">
                <CodeTagsIcon class="tabs-icon" />
                Flow code
            </el-checkbox-button>
            <el-checkbox-button name="nocode" value="nocode">
                <MouseRightClickIcon class="tabs-icon" />
                Flow no-code
            </el-checkbox-button>
            <el-checkbox-button name="topology" value="topology">
                <FileTreeOutlineIcon class="tabs-icon" />
                Topology
            </el-checkbox-button>
            <el-checkbox-button name="doc" value="doc">
                <FileDocumentIcon class="tabs-icon" />
                Documentation
            </el-checkbox-button>
            <el-checkbox-button name="files" value="files">
                <DotsSquareIcon class="tabs-icon" />
                Files
            </el-checkbox-button>
            <el-checkbox-button name="blueprints" value="blueprints">
                <BallotOutlineIcon class="tabs-icon" />
                Blueprints
            </el-checkbox-button>
        </el-checkbox-group>
    </div>
    <Splitpanes>
        <Pane v-if="activeTabs.includes('code')">
            <EditorWrapper />
        </Pane>
        <Pane v-if="activeTabs.includes('nocode')">
            <NoCodeWrapper />
        </Pane>
        <Pane v-if="activeTabs.includes('topology')">
            <LowCodeEditorWrapper />
        </Pane>
        <Pane v-if="activeTabs.includes('doc')">
            <PluginDocumentation />
        </Pane>
    </Splitpanes>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"

    import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
    import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
    import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue";
    import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
    import DotsSquareIcon from "vue-material-design-icons/DotsSquare.vue";
    import BallotOutlineIcon from "vue-material-design-icons/BallotOutline.vue";

    import EditorWrapper from "../inputs/EditorWrapper.vue";

    import NoCodeWrapper from "../code/NoCodeWrapper.vue";

    import LowCodeEditorWrapper from "../inputs/LowCodeEditorWrapper.vue";

    import PluginDocumentation from "../plugins/PluginDocumentation.vue";


    const activeTabs = ref<string[]>([])
</script>

<style lang="scss" scoped>
    .tabs{
        padding: .5rem 1rem;
        border-bottom: 1px solid var(--ks-border-primary);
    }

    .tabs-icon {
        margin-right: .25rem;
        vertical-align: bottom;
    }
</style>