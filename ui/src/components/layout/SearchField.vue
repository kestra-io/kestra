<template>
    <el-input
        v-model="search"
        @input="onInput"
        :placeholder="$t(placeholder)"
        :readonly="readonly"
        clearable
    >
        <template #prefix>
            <slot name="prefix" />
        </template>
        <template #suffix>
            <div class="shortcut d-flex">
                <slot name="suffix">
                    <Magnify />
                </slot>
            </div>
        </template>
    </el-input>
</template>

<script lang="ts" setup>
    import {ref, watch, onMounted, onUnmounted} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import debounce from "lodash/debounce";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    const props = withDefaults(defineProps<{
        router?: boolean;
        placeholder?: string;
        readonly?: boolean;
    }>(), {
        router: true,
        placeholder: "search",
        readonly: false
    });

    const emit = defineEmits<{
        (e: "search", value: string): void;
    }>();

    const route = useRoute();
    const vueRouter = useRouter();

    const search = ref<string>("");

    let searchDebounce: ReturnType<typeof debounce>;

    function init() {
        if (route.query.q && props.router !== false) {
            search.value = String(route.query.q);
        }
        searchDebounce = debounce(() => {
            emit("search", search.value);
            if (props.router !== false) {
                const query: Record<string, any> = {
                    ...route.query,
                    q: search.value,
                    page: 1
                };
                if (!search.value) {
                    delete query.q;
                }
                vueRouter.push({query});
            }
        }, 300);
    }

    function onInput() {
        searchDebounce();
    }

    onMounted(() => {
        init();
    });

    onUnmounted(() => {
        if (searchDebounce) searchDebounce.cancel();
    });

    watch(
        () => route.query.q,
        (newQ) => {
            search.value = newQ ? String(newQ) : "";
        }
    );
</script>

<style scoped lang="scss">
    .shortcut {
        font-size: 0.75rem;
        line-height: 1.25rem;
        gap: .25rem;
    }

    .el-input {
        :deep(.el-input__prefix), :deep(input)::placeholder {
            color: var(--ks-content-primary);
        }
    }
</style>