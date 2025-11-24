<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb">
        <template #additional-right v-if="canSave || canDelete">
            <ul>
                <li>
                    <el-button :icon="Delete" size="large" type="default" v-if="canDelete" @click="deleteFile">
                        {{ $t('delete') }}
                    </el-button>

                    <template v-if="canSave">
                        <el-button :icon="ContentSave" @click="save" type="primary" size="large">
                            {{ $t('save') }}
                        </el-button>
                    </template>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <TemplatesDeprecated />
    <section class="container d-flex flex-fill">
        <Editor 
            @save="save" 
            v-model="content" 
            schemaType="template" 
            lang="yaml"
            @update:model-value="onChange"
            @cursor="updatePluginDocumentation"
            class="w-100 h-auto"
        />
    </section>
</template>

<script setup lang="ts">
    import {onMounted, onUnmounted, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    // @ts-expect-error no types available
    import TemplatesDeprecated from "./TemplatesDeprecated.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import {useFlowTemplateEdit} from "../../composables/useFlowTemplateEdit";
    import useRouteContext from "../../composables/useRouteContext";
    import {useTemplateStore} from "../../stores/template";
    import Editor from "../inputs/Editor.vue";
    import {useUnsavedChangesStore} from "../../stores/unsavedChanges";

    const route = useRoute();
    const router = useRouter();
    const templateStore = useTemplateStore();
    const unsavedChangesStore = useUnsavedChangesStore();

    const dataType = "template";

    const {
        content,
        previousContent,
        routeInfo,
        canSave,
        canDelete,
        loadFile,
        deleteFile,
        save,
        updatePluginDocumentation
    } = useFlowTemplateEdit(
        dataType,
        route,
        router,
        undefined,
        undefined,
        undefined,
        undefined
    );

    const onChange = () => {
        unsavedChangesStore.unsavedChange = previousContent.value !== content.value;
    };

    const reload = () => {
        if (route.name === "templates/update") {
            templateStore
                .loadTemplate(route.params as { namespace: string; id: string })
                .then(loadFile);
        }
    };

    useRouteContext(routeInfo, false);

    watch(
        () => route.params,
        () => {
            reload();
        },
        {deep: true}
    );

    onMounted(() => {
        reload();
    });

    onUnmounted(() => {
        templateStore.template = undefined;
    });
</script>
