<template>
    <div @click="handleClick" class="d-flex my-2 p-2 rounded element">
        <div class="me-2 icon">
            <TaskIcon :cls="props.element.type" :icons only-icon />
        </div>

        <div class="flex-grow-1 label">
            {{ props.element.id }}
        </div>

        <el-button
            @click.prevent.stop="emits('removeElement')"
            :icon="DeleteOutline"
            size="small"
            class="border-0"
        />
        <div class="d-flex flex-column">
            <ChevronUp @click.prevent.stop="emits('moveElement', 'up')" />
            <ChevronDown @click.prevent.stop="emits('moveElement', 'down')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../utils/icons";

    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    const emits = defineEmits(["removeElement", "moveElement"]);

    const props = defineProps({
        section: {type: String, required: true},
        element: {type: Object, required: true},
    });

    import {useStore} from "vuex";
    const store = useStore();

    const icons = computed(() => store.state.plugin.icons);

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    const handleClick = () => {
        router.replace({
            query: {
                ...route.query,
                section: props.section.toLowerCase(),
                identifier: props.element.id,
                type: props.element.type,
            },
        });
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.element {
    cursor: pointer;
    background-color: $code-card-color;
    border: 1px solid $code-border-color;

    & > .icon {
        width: 1.25rem;
    }

    & > .label {
        color: initial;
        font-size: $code-font-sm;
    }
}
</style>
