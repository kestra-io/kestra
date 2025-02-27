<template>
    <div class="tabs">
        <el-checkbox-group v-model="activeTabs">
            <el-checkbox-button v-for="element of EDITOR_ELEMENTS" :key="element.value" :value="element.value" :name="element.value">
                <component class="tabs-icon" :is="element.button.icon" />
                {{ element.button.label }}
            </el-checkbox-button>
        </el-checkbox-group>
    </div>
    <Splitpanes class="default-theme">
        <Pane v-for="element in visibleTabs" :key="element.value">
            <component :is="element.component" />
        </Pane>
    </Splitpanes>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"

    import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
    import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
    import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue";
    import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
    import DotsSquareIcon from "vue-material-design-icons/DotsSquare.vue";
    import BallotOutlineIcon from "vue-material-design-icons/BallotOutline.vue";

    import EditorSidebarWrapper from "../inputs/EditorSidebarWrapper.vue";
    import EditorWrapper from "../inputs/EditorWrapper.vue";
    import NoCodeWrapper from "../code/NoCodeWrapper.vue";
    import LowCodeEditorWrapper from "../inputs/LowCodeEditorWrapper.vue";
    import PluginDocumentationWrapper from "../plugins/PluginDocumentationWrapper.vue";
    import BlueprintsWrapper from "../flows/blueprints/BlueprintsWrapper.vue";

    const activeTabs = ref(["code", "doc"])

    const visibleTabs = computed(() => {
        return EDITOR_ELEMENTS.filter((element) => activeTabs.value.includes(element.value))
    })

    const EDITOR_ELEMENTS = [
        {
            button: {
                icon: CodeTagsIcon,
                label: "Code"
            },
            value: "code",
            component: EditorWrapper
        },
        {
            button: {
                icon: MouseRightClickIcon,
                label: "No-code"
            },
            value: "nocode",
            component: NoCodeWrapper
        },
        {
            button: {
                icon: FileTreeOutlineIcon,
                label: "Topology"
            },
            value: "topology",
            component: LowCodeEditorWrapper
        },
        {
            button: {
                icon: FileDocumentIcon,
                label: "Documentation"
            },
            value: "doc",
            component: PluginDocumentationWrapper
        },
        {
            button: {
                icon: DotsSquareIcon,
                label: "Files"
            },
            value: "files",
            component: EditorSidebarWrapper
        },
        {
            button: {
                icon: BallotOutlineIcon,
                label: "Blueprints"
            },
            value: "blueprints",
            component: BlueprintsWrapper
        }
    ]
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
