<template>
    <TaskEditor
        v-if="!lastBreadcumb.shown"
        v-model="yaml"
        :section
        @update:model-value="validateTask"
    />

    <component
        v-else
        :is="lastBreadcumb.component.type"
        v-bind="lastBreadcumb.component.props"
        :model-value="lastBreadcumb.component.props.modelValue"
        @update:model-value="validateTask"
    />

    <template v-if="yaml">
        <!-- TODO: Improve the validation for single tasks -->
        <ValidationError v-if="false" :errors link />

        <Save
            @click="saveTask"
            :what="route.query.section?.toString()"
            class="w-100 mt-3"
        />
    </template>
</template>

<script setup lang="ts">
    import {onBeforeMount, ref, watch, computed} from "vue";

    const emits = defineEmits(["updateTask", "updateDocumentation"]);
    const props = defineProps({
        flow: {type: String, required: true},
        creation: {type: Boolean, default: false},
    });

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    import {SECTIONS} from ".././../../utils/constants";
    const section = ref(SECTIONS.TASKS);

    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import YamlUtils from "../../../utils/yamlUtils";

    import {useStore} from "vuex";
    const store = useStore();

    const breadcrumbs = computed(() => store.state.code.breadcrumbs);
    const lastBreadcumb = computed(() => {
        const index =
            breadcrumbs.value.length === 3 ? 2 : breadcrumbs.value.length - 1;

        return {
            shown: index >= 2,
            component: breadcrumbs.value?.[index]?.component,
        };
    });

    const yaml = ref(
        YamlUtils.extractTask(props.flow, route.query.identifier)?.toString() || "",
    );

    onBeforeMount(() => {
        const type = YamlUtils.parse(yaml.value)?.type ?? null;
        emits("updateDocumentation", type);
    });

    watch(
        () => route.query.section,
        (value) => {
            section.value = SECTIONS[value === "triggers" ? "TRIGGERS" : "TASKS"];
        },
        {immediate: true},
    );

    watch(
        () => route.query.identifier,
        (value) => {
            if (value === "new") {
                yaml.value = "";
            } else {
                yaml.value =
                    YamlUtils.extractTask(props.flow, value)?.toString() || "";
            }
        },
        {immediate: true},
    );

    import ValidationError from "../../../components/flows/ValidationError.vue";

    const CURRENT = ref(null);
    const validateTask = (task) => {
        let temp = YamlUtils.parse(yaml.value);

        if (lastBreadcumb.value.shown) {
            const field = breadcrumbs.value.at(-1).label;
            temp = {...temp, [field]: task};
        }

        temp = YamlUtils.stringify(temp);

        store
            .dispatch("flow/validateTask", {task: temp, section: section.value})
            .then(() => (yaml.value = temp));

        CURRENT.value = temp;
    };

    const errors = computed(() => store.getters["flow/taskError"]);

    import Save from "../components/Save.vue";
    const saveTask = () => {
        if (lastBreadcumb.value.shown) {
            store.commit("code/removeBreadcrumb", {last: true});
            return;
        }

        const source = props.flow;

        const task = YamlUtils.extractTask(
            yaml.value,
            YamlUtils.parse(yaml.value).id,
        );

        const currentSection = route.query.section;
        const isCreation =
            props.creation &&
            (!route.query.identifier || route.query.identifier === "new");

        let result;

        if (isCreation) {
            if (currentSection === "tasks") {
                const existing = YamlUtils.checkTaskAlreadyExist(
                    source,
                    CURRENT.value,
                );

                if (existing) {
                    store.dispatch("core/showMessage", {
                        variant: "error",
                        title: "Task with same ID already exist",
                        message: `Task in ${route.query.section} block  with ID: ${existing} already exist in the flow.`,
                    });
                    return;
                }

                result = YamlUtils.insertTask(
                    source,
                    route.query.target ?? YamlUtils.getLastTask(source),
                    task,
                    route.query.position ?? "after",
                );
            } else if (currentSection === "triggers") {
                result = YamlUtils.insertTrigger(source, CURRENT.value);
            } else if (currentSection === "error handlers") {
                result = YamlUtils.insertError(source, CURRENT.value);
            } else if (currentSection === "finally") {
                result = YamlUtils.insertFinally(source, CURRENT.value);
            }
        } else {
            result = YamlUtils.replaceTaskInDocument(
                source,
                route.query.identifier,
                task,
            );
        }

        emits("updateTask", result);
        store.commit("code/removeBreadcrumb", {last: true});

        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {section, identifier, type, ...rest} = route.query;
        router.replace({query: {...rest}});
    };
</script>
