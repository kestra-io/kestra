const getItem = (key) => JSON.parse(localStorage.getItem(key) || "[]");
const setItem = (key, value) => localStorage.setItem(key, JSON.stringify(value));
const filterItems = (items, element) => items.filter((item) => JSON.stringify(item) !== JSON.stringify(element));

export function useFilters(prefix) {
    const keys = {recent: `recent__${prefix}`, saved: `saved__${prefix}`};

    return {
        getRecentItems: () => getItem(keys.recent),
        setRecentItems: (value) => setItem(keys.recent, value),
        removeRecentItem: (element) => setItem(keys.recent, filterItems(getItem(keys.recent), element)),

        getSavedItems: () => getItem(keys.saved),
        setSavedItems: (value) => setItem(keys.saved, value),
        removeSavedItem: (element) => setItem(keys.saved, filterItems(getItem(keys.saved), element)),
    };
}
