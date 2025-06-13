function isNullOrUndefined(value: any): boolean {
    return value === null || value === undefined;
}

export function removeNullAndUndefined(obj: any): any {
    if (Array.isArray(obj)) {
        return obj
            .map(item => removeNullAndUndefined(item))
            .filter(item => isNullOrUndefined(item) === false);

    }
    if (typeof obj === "object") {
        const newObj: any = {};
        let hasValue = false;
        for (const key in obj) {
            const rawValue = obj[key]
            if(isNullOrUndefined(rawValue)) {
                continue;
            }
            const newVal = removeNullAndUndefined(rawValue);
            if(isNullOrUndefined(newVal)) {
                continue;
            }
            hasValue = true;
            newObj[key] = newVal;
        }
        return hasValue ? newObj : undefined;
    }
    return obj;
}
