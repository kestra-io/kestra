import {computed, nextTick, onMounted, ref} from "vue";
import {RouteLocation, useRoute, useRouter} from "vue-router";

interface UseRestoreUrlOptions {
  restoreUrl?: boolean;
  isDefaultNamespaceAllow?: boolean;
}

type QueryLike = Record<string, unknown>;

const stripSearchFromQuery = (query: QueryLike): QueryLike => {
  const cleaned: QueryLike = {...query};

  // legacy keys
  delete cleaned.q;
  delete cleaned.search;

  // encoded filter keys
  for (const k of Object.keys(cleaned)) {
    if (k === "filters[q][EQUALS]" || k.startsWith("filters[q]")) {
      delete cleaned[k];
    }
  }

  return cleaned;
};

function getLocalStorageName(route: RouteLocation): string {
  const tenant = route.params.tenant;
  return `${route.name?.toString().replace("/", "_")}${route.params.tab ? "_" + route.params.tab : ""}${
    tenant ? "_" + tenant : ""
  }_restore_url`;
}

function getRestoredUrlValue(route: RouteLocation): QueryLike | null {
  const localStorageName = getLocalStorageName(route);
  const localStorageValue = window.sessionStorage.getItem(localStorageName);
  return localStorageValue ? (JSON.parse(localStorageValue) as QueryLike) : null;
}

export function getRestoredQuery(route: RouteLocation) {
  const localStorageValue = getRestoredUrlValue(route);

  if (localStorageValue === null) {
    return {
      query: route.query,
      change: false,
      localStorageValue,
    };
  }

  // NOTE: route.query is typically empty when restore runs, but keep this safe anyway.
  const query: QueryLike = stripSearchFromQuery({...(route.query as QueryLike)});
  const local: QueryLike = stripSearchFromQuery(localStorageValue);

  let change = false;

  for (const key in local) {
    // only add keys that are missing from current query
    if (query[key] == null && local[key] != null) {
      // empty array breaks the application
      if (Array.isArray(local[key]) && (local[key] as unknown[]).length === 0) continue;

      query[key] = local[key];
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
  const {restoreUrl = true} = options;

  const route = useRoute();
  const router = useRouter();

  const loadInit = ref(true);

  const localStorageName = computed(() => getLocalStorageName(route));

  const localStorageValue = computed<QueryLike | null>(() => {
    const raw = window.sessionStorage.getItem(localStorageName.value);
    return raw ? (JSON.parse(raw) as QueryLike) : null;
  });

  const saveRestoreUrl = () => {
    if (!restoreUrl) return;

    const toPersist = stripSearchFromQuery(route.query as QueryLike);

    if (Object.keys(toPersist).length === 0) {
      window.sessionStorage.removeItem(localStorageName.value);
    } else {
      window.sessionStorage.setItem(localStorageName.value, JSON.stringify(toPersist));
    }
  };

  const goToRestoreUrl = () => {
    const {query, change} = getRestoredQuery(route);

    if (change) {
      nextTick(() => {
        router.replace({query: query as any});
      });
    } else {
      loadInit.value = true;
    }
  };

  onMounted(() => {
    if (restoreUrl && localStorageValue.value) {
      if (!route.query || Object.keys(route.query).length === 0) {
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
    goToRestoreUrl,
  };
}






/*import {computed, nextTick, onMounted, ref} from "vue";
import {RouteLocation, useRoute, useRouter} from "vue-router";

interface UseRestoreUrlOptions {
    restoreUrl?: boolean;
    isDefaultNamespaceAllow?: boolean;
}

//new code
const stripSearchFromQuery = (query: Record<string, any>) => {
    const cleaned = { ...query };
  
    delete cleaned.q;
  
    Object.keys(cleaned).forEach((k) => {
      if (k.startsWith("filters[q]")) delete cleaned[k];
    });
  
    return cleaned;
  };

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
    if(localStorageValue === null){
        return {
            query: route.query,
            change: false,
            localStorageValue,
        };
    };
 
//new code
      const query = stripSearchFromQuery({ ...route.query } as any);
      const local = stripSearchFromQuery({ ...localStorageValue } as any);
      let change = false;

    for (const key in local) {
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

    //newest code
    const saveRestoreUrl = () => {
          if (!restoreUrl) return;
        
          const toPersist = stripSearchFromQuery(route.query as any);
        
          if (Object.keys(toPersist).length === 0) {
            window.sessionStorage.removeItem(localStorageName.value);
          } else {
            window.sessionStorage.setItem(localStorageName.value, JSON.stringify(toPersist));
          }
        };

    const router = useRouter();

    /**
     * Merges saved URL query parameters from sessionStorage with current route.
     * Only adds missing parameters to avoid overwriting user changes.
     * Updates route only when changes are made.
     */
    /*
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
    /*
    onMounted(() => {
        if (restoreUrl && localStorageValue.value){
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
*/