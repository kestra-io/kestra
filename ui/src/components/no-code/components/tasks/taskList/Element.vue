<template>
    <div @click="handleClick" class="d-flex my-2 p-2 rounded element" :class="{'moved': moved}">
        <div v-if="!['inputs', 'layout'].includes(props.parentPathComplete)" class="me-2 icon">
            <TaskIcon :cls="element.type" :icons="pluginsStore.icons" onlyIcon />
        </div>

        <div class="flex-grow-1 label">
            {{ identifier }}
        </div>

        <button v-if="playgroundStore.enabled && element.id && isTask" @click.stop="playgroundStore.runUntilTask(element.id)" type="button" class="playground-run-task">
            <PlayIcon />
        </button>

        <el-button
            @click.prevent.stop="emits('removeElement')"
            :icon="DeleteOutline"
            size="small"
            class="border-0"
        />
        <div v-if="elementIndex !== undefined" class="d-flex flex-column">
            <ChevronUp @click.prevent.stop="emits('moveElement', 'up')" />
            <ChevronDown @click.prevent.stop="emits('moveElement', 'down')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue";
    import {useI18n} from "vue-i18n";
    import PlayIcon from "vue-material-design-icons/Play.vue";
    import {usePluginsStore} from "../../../../../stores/plugins";
    import {usePlaygroundStore} from "../../../../../stores/playground";


    import {DeleteOutline, ChevronUp, ChevronDown} from "../../../utils/icons";
    import {
        EDIT_TASK_FUNCTION_INJECTION_KEY,
    } from "../../../injectionKeys";

    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    const emits = defineEmits(["removeElement", "moveElement"]);

    const {t} = useI18n();

    const props = defineProps<{
        section: string;
        parentPathComplete: string;
        element: {
            id?: string;
            type?: string;
            on?: string;
        };
        blockSchemaPath: string;
        elementIndex?: number;
        typeFieldSchema: "on" | "type";
        moved?: boolean;
    }>();

    const pluginsStore = usePluginsStore();
    const playgroundStore = usePlaygroundStore();

    const isTask = computed(() => ["tasks", "task"].includes(props.parentPathComplete.split(".").pop() ?? "not-found"));

    const editTask = inject(EDIT_TASK_FUNCTION_INJECTION_KEY, () => {});

    const identifier = computed(() => {
        return props.element.id
            ?? props.element[props.typeFieldSchema]
            ?? `<${t("no_code.unnamed")} ${props.elementIndex}>`;
    });

    const handleClick = () => {
        editTask(
            props.parentPathComplete,
            props.blockSchemaPath,
            props.elementIndex
        );
    };
</script>

<style scoped lang="scss">
@import "../../../styles/code.scss";
@import "@kestra-io/ui-libs/src/scss/_color-palette";

.element {
    cursor: pointer;
    background-color: $code-card-color;
    border: 1px solid $code-border-color;
    transition: all 0.2s ease-in-out;

    &:hover {
        background-color: var(--ks-button-background-secondary-hover);
    }

    & > .icon {
        width: 1.25rem;
    }

    & > .label {
        color: inherit;
        font-size: $code-font-sm;
    }

    &.moved {
        background-color: var(--ks-button-background-secondary-active);
        border-color: var(--ks-border-active);
    }

    .playground-run-task{
        color: $base-white;
        background-color: $base-blue-400;
        height: 16px;
        width: 16px;
        font-size: 4px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-top: 4px;
        padding: 0;
        border: none;
    }

    :deep(.el-button) {
        background-color: transparent;

        &:hover {
            color: var(--ks-content-alert);
        }
    }
}
</style>
