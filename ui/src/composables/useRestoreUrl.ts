import {computed, nextTick, onMounted, ref} from "vue";
import {useRoute, useRouter} from "vue-router";

interface UseRestoreUrlOptions {
    restoreUrl?: boolean;
    isDefaultNamespaceAllow?: boolean;
}

export default function useRestoreUrl(options: UseRestoreUrlOptions = {}) {
    const {
        restoreUrl = true,
        isDefaultNamespaceAllow = false
    } = options;

    const route = useRoute();
    const router = useRouter();
    const loadInit = ref(true);

    const localStorageName = computed(() => {
        const tenant = route.params.tenant;
        return `${route.name?.toString().replace("/", "_")}${route.params.tab ? "_" + route.params.tab : ""}${tenant ? "_" + tenant : ""}_restore_url`;
    });

    const localStorageValue = computed(() => {
        if (window.sessionStorage.getItem(localStorageName.value)) {
            return JSON.parse(window.sessionStorage.getItem(localStorageName.value)!);
        } else {
            return null;
        }
    });

    const saveRestoreUrl = () => {
        if (!restoreUrl) {
            return;
        }

        if (Object.keys(route.query).length > 0 || (localStorageValue.value !== null && Object.keys(localStorageValue.value).length > 0)) {
            if (Object.keys(route.query).length === 0) {
                window.sessionStorage.removeItem(localStorageName.value);
            } else {
                window.sessionStorage.setItem(
                    localStorageName.value,
                    JSON.stringify(route.query)
                );
            }
        }
    };

    const goToRestoreUrl = () => {
        if (!restoreUrl) {
            return;
        }

        const localExist = localStorageValue.value !== null;
        const query = {...route.query};
        const local = localStorageValue.value === null ? {} : {...localStorageValue.value};

        let change = false;

        if (!localExist && isDefaultNamespaceAllow && localStorage.getItem("defaultNamespace")) {
            local["namespace"] = localStorage.getItem("defaultNamespace");
        }

        for (const key in local) {
            if (!query[key] && local[key]) {
                // empty array break the application
                if (local[key] instanceof Array && local[key].length === 0) {
                    continue;
                }

                query[key] = local[key];
                change = true;
            }
        }

        if (change) {
            // wait for the router to be ready
            nextTick(() => {
                router.replace({query: query});
            });
        } else {
            loadInit.value = true;
        }
    };

    // Automatically call goToRestoreUrl on mount if needed (equivalent to created() hook)
    onMounted(() => {
        if (Object.keys(route.query).length === 0 && restoreUrl) {
            loadInit.value = false;
            goToRestoreUrl();
        }
    });

    return {
        loadInit,
        localStorageName,
        localStorageValue,
        saveRestoreUrl,
        goToRestoreUrl
    };
}
