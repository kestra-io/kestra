import {Comparators} from "../components/filter/utils/filterTypes";
import {getComparator} from "../components/filter/utils/helpers";

type LegacyFilter = { field: string, operation: (keyof typeof Comparators) | "IN" | "NOT_IN", value: string };
const getItem: (key: string) => { name: string, value: string | LegacyFilter[] }[] = (key) => {
    return JSON.parse(localStorage.getItem(key) || "[]");
};

const setItem = (key: string, value: object) => {
    return localStorage.setItem(key, JSON.stringify(value));
};

export const compare = (item: object, element: object) => {
    return JSON.stringify(item) !== JSON.stringify(element);
};

const filterItems = (items: object[], element: object) => {
    return items.filter((item) => compare(item, element));
};

export function useFilters(prefix: string) {
    const keys = {saved: `saved__${prefix}`};

    function setSavedItems(value: object) {
        return setItem(keys.saved, value);
    }

    return {
        getSavedItems: () => {
            let hasLegacyFilter = false;
            const savedItems = getItem(keys.saved).map(({name, value}) => {
                let stringValue;
                if (typeof value === "string") {
                    stringValue = value;
                } else {
                    hasLegacyFilter = true;

                    const comparator = (operation: LegacyFilter["operation"]) => getComparator(operation);
                    stringValue = (value as LegacyFilter[]).map(f =>
                        `${f.field}${comparator(f.operation)}${f.value.includes(" ") ? `"${f.value}"` : f.value}`
                    ).join(" ")
                }

                return {name, value: stringValue}
            });

            if (hasLegacyFilter) {
                setSavedItems(savedItems);
            }

            return savedItems;
        },
        setSavedItems,
        removeSavedItem: (element: object) => {
            const filtered = filterItems(getItem(keys.saved), element);
            return setItem(keys.saved, filtered);
        }
    };
}