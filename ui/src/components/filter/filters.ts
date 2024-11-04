const RECENTS = "recent";
const SAVED = "saved";

export const getRecentItems = (prefix) => {
    const recents = localStorage.getItem(`${RECENTS}__${prefix}`);
    return recents ? JSON.parse(recents) : [];
};

export const setRecentItems = (prefix, value) => {
    localStorage.setItem(`${RECENTS}__${prefix}`, JSON.stringify(value));
};

export const removeRecentItem = (prefix, element) => {
    let recents = getRecentItems(prefix).filter(
        (item) => JSON.stringify(item) !== JSON.stringify(element),
    );
    setRecentItems(prefix, recents);
};

export const getSavedItems = (prefix) => {
    const saved = localStorage.getItem(`${SAVED}__${prefix}`);
    return saved ? JSON.parse(saved) : [];
};

export const setSavedItems = (prefix, value) => {
    localStorage.setItem(`${SAVED}__${prefix}`, JSON.stringify(value));
};

export const removeSavedItem = (prefix, element) => {
    let saved = getSavedItems(prefix).filter(
        (item) => JSON.stringify(item) !== JSON.stringify(element),
    );
    setSavedItems(prefix, saved);
};
