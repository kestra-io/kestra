import {computed, nextTick, onMounted, ref} from "vue";
import {RouteLocation, useRoute, useRouter} from "vue-router";
import {storageKeys} from "../utils/constants";

const PAGE_SIZES = [10, 25, 50, 100];

export function paginationSizeKey(route: RouteLocation): string {
    return `${storageKeys.PAGINATION_SIZE}__${String(route.name)}`;
}

/** Unknown values are ignored rather than shown, so a stale or hand-edited entry cannot reach the page-size control. */
function getStoredSize(route: RouteLocation): string | undefined {
    const stored = Number(window.localStorage?.getItem(paginationSizeKey(route)));
    return PAGE_SIZES.includes(stored) ? String(stored) : undefined;
}

interface UseRestoreUrlOptions {
    restoreUrl?: boolean;
    isDefaultNamespaceAllow?: boolean;
}

function getLocalStorageName(route: RouteLocation): string {
    const tenant = route.params.tenant;
    return `${route.name?.toString().replace("/", "_")}${route.params.tab ? "_" + route.params.tab : ""}${tenant ? "_" + tenant : ""}_restore_url`;
}

function getRestoredUrlValue(route: RouteLocation) {
    const localStorageName = getLocalStorageName(route);
    const localStorageValue = window.sessionStorage.getItem(localStorageName);
    if (localStorageValue) {
        return JSON.parse(localStorageValue);
    } else {
        return null;
    }
}

export function getRestoredQuery(route: RouteLocation) {
    const localStorageValue = getRestoredUrlValue(route);
    const query = {...route.query};

    let change = false;

    if (localStorageValue !== null) {
        const local = {...localStorageValue};

        for (const key in local) {
            if (key === "page") {
                continue;
            }
            if (!query[key] && local[key]) {
                // empty array break the application
                if (local[key] instanceof Array && local[key].length === 0) {
                    continue;
                }

                if(local[key] === query[key]){
                    continue;
                }

                query[key] = local[key];
                change = true;
            }
        }
    }

    // The size rides along with the restore so it reaches the URL in the same navigation as the
    // filters: pushed afterwards it is dropped by the default-filter replace on pages that have one.
    if (!query.size) {
        const storedSize = getStoredSize(route);
        if (storedSize) {
            query.size = storedSize;
            change = true;
        }
    }

    return {
        query,
        change, 
        localStorageValue,
    };
}

export default function useRestoreUrl(options: UseRestoreUrlOptions = {}) {
    const {
        restoreUrl = true,
    } = options;

    const route = useRoute();

    const loadInit = ref(true);

    const localStorageName = computed(() => getLocalStorageName(route));

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

        if (route.query.noRestore) {
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

    const router = useRouter();

    /**
     * Merges saved URL query parameters from sessionStorage with current route.
     * Only adds missing parameters to avoid overwriting user changes.
     * Updates route only when changes are made.
     */
    const goToRestoreUrl = () => {
        const {query, change} = getRestoredQuery(route);

        if (change) {
            // wait for the router to be ready
            nextTick(() => {
                router.replace({query});
            });
        } else {
            loadInit.value = true;
        }
    };

    /**
     * Automatically restores saved URL state from sessionStorage on mount.
     * Only triggers when restoreUrl is enabled and saved state exists.
     */
    onMounted(() => {
        if (restoreUrl && (localStorageValue.value || getStoredSize(route))){
            if(!route.query || Object.keys(route.query).length === 0) {
                loadInit.value = false;
                goToRestoreUrl();
            }
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
