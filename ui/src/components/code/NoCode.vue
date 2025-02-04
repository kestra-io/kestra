<template>
    <div class="h-100 overflow-y-auto no-code">
        <Breadcrumbs :flow="YamlUtils.parse(props.flow)" />

        <hr class="m-0">

        <Editor
            :creation="
                route.query.identifier === 'new' ||
                    route.name === 'flows/create'
            "
            :flow
            :metadata
            @update-metadata="(k, v) => emits('updateMetadata', {[k]: v})"
            @update-task="(yaml) => emits('updateTask', yaml)"
            @reorder="(yaml) => emits('reorder', yaml)"
            @update-documentation="(task) => emits('updateDocumentation', task)"
        />
    </div>
</template>

<script setup lang="ts">
    import {onBeforeMount, computed} from "vue";

    import YamlUtils from "../../utils/yamlUtils";

    import Breadcrumbs from "./components/Breadcrumbs.vue";
    import Editor from "./segments/Editor.vue";

    const emits = defineEmits([
        "updateTask",
        "updateMetadata",
        "updateDocumentation",
        "reorder",
    ]);
    const props = defineProps({
        flow: {type: String, required: true},
    });

    const metadata = computed(() => YamlUtils.getMetadata(props.flow));

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    onBeforeMount(async () => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {section, identifier, type, ...rest} = route.query;
        router.replace({query: {...rest}});
    });
</script>

<style scoped lang="scss">
@import "./styles/code.scss";
</style>
