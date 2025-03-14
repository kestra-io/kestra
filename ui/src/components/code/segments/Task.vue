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
        identifier: {type: String, required: true},
        flow: {type: String, required: true},
        creation: {type: Boolean, default: false},
    });

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    import {SECTIONS} from ".././../../utils/constants";
    const section = ref(SECTIONS.TASKS);

    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

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
        YAML_UTILS.extractTask(props.flow, props.identifier)?.toString() || "",
    );

    onBeforeMount(() => {
        const type = YAML_UTILS.parse(yaml.value)?.type ?? null;
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
        () => props.identifier,
        (value) => {
            if (value === "new") {
                yaml.value = "";
            } else {
                yaml.value =
                    YAML_UTILS.extractTask(props.flow, value)?.toString() || "";
            }
        },
        {immediate: true},
    );

    import ValidationError from "../../../components/flows/ValidationError.vue";

    const CURRENT = ref(null);
    const validateTask = (task) => {
        let temp = YAML_UTILS.parse(yaml.value);

        if (lastBreadcumb.value.shown) {
            const field = breadcrumbs.value.at(-1).label;
            temp = {...temp, [field]: task};
        }

        temp = YAML_UTILS.stringify(temp);

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

        const task = YAML_UTILS.extractTask(
            yaml.value,
            YAML_UTILS.parse(yaml.value).id,
        );

        const currentSection = route.query.section;
        const isCreation =
            props.creation && (!props.identifier || props.identifier === "new");

        let result;

        if (isCreation) {
            if (currentSection === "tasks") {
                const existing = YAML_UTILS.checkTaskAlreadyExist(
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

                result = YAML_UTILS.insertTask(
                    source,
                    route.query.target ?? YAML_UTILS.getLastTask(source),
                    task,
                    route.query.position ?? "after",
                );
            } else if (currentSection === "triggers") {
                result = YAML_UTILS.insertSection("triggers", source, CURRENT.value);
            } else if (currentSection === "error handlers") {
                result = YAML_UTILS.insertSection("errors", source, CURRENT.value);
            } else if (currentSection === "finally") {
                result = YAML_UTILS.insertSection("finally", source, CURRENT.value);
            } else if (currentSection === "after execution") {
                result = YAML_UTILS.insertSection("afterExecution", source, CURRENT.value);
            }
        } else {
            result = YAML_UTILS.replaceTaskInDocument(
                source,
                props.identifier,
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
