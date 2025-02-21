export const encodeParams = (route, filters, OPTIONS) => {
    if(isSearchPath(route)) {return encodeSearchParams(filters, OPTIONS); }

    const encode = (values, key) => {
        return values
            .map((v) => {
                if (key === "childFilter" && v === "ALL") return null;
                else if(key === "q") return v;

                const encoded = encodeURIComponent(v);
                return key === "labels"
                    ? encoded.replace(/%3A/g, ":")
                    : encoded;
            })
            .filter((v) => v !== null);
    };
    return filters.reduce((query, filter) => {
        const match = OPTIONS.find((o) => o.value.label === filter.label);
        const key = match ? match.key : filter.label === "text" ? "q" : null;

        if (key) {
            if (key === "details") {
                match.value.value.forEach((item) => {
                    const value = item.split(":");
                    if (value.length === 2) {
                        query[`details.${value[0]}`] = value[1];
                    }
                });
            }
            if (key !== "date") query[key] = encode(filter.value, key);
            else {
                if(filter.value?.length > 0) {
                    const {startDate, endDate} = filter.value[0];

                    query.startDate = startDate;
                    query.endDate = endDate;
                }
            }
        }

        delete query.details;

        return query;
    }, {});
};

export const decodeParams = (route, query, include, OPTIONS) => {
    if(isSearchPath(route)) {return decodeSearchParams(query, include, OPTIONS); }


    let params = Object.entries(query)
        .filter(
            ([key]) =>
                key === "q" ||
                OPTIONS.some(
                    (o) => o.key === key && include.includes(o.value.label),
                ),
        )
        .map(([key, value]) => {
            if (key.startsWith("details.")) {
                // Handle details.* keys
                const detailKey = key.replace("details.", ""); // Extract key after 'details.'
                return {label: "details", value: `${detailKey}:${value}`};
            }

            const label =
                key === "q"
                    ? "text"
                    : OPTIONS.find((o) => o.key === key)?.value.label || key;

            const decodedValue = Array.isArray(value)
                ? value.map(decodeURIComponent)
                : [decodeURIComponent(value)];

            return {label, value: decodedValue};
        });

    // Group all details into a single entry
    const details = params
        .filter((p) => p.label === "details")
        .map((p) => p.value); // Collect all `details` values

    if (details.length > 0) {
        // Replace multiple details with a single object
        params = params.filter((p) => p.label !== "details"); // Remove individual details
        params.push({label: "details", value: details});
    }

    // Handle the date functionality by grouping startDate and endDate if they exist
    if (query.startDate && query.endDate) {
        params.push({
            label: "absolute_date",
            value: [{startDate: query.startDate, endDate: query.endDate}],
        });
    }

    // TODO: Will need tweaking once we introduce multiple comparators for filters
    return params.map((p) => {
        const comparator = OPTIONS.find((o) => o.value.label === p.label);
        return {...p, comparator: comparator?.comparators?.[0]};
    });
};


export const encodeSearchParams = (filters, OPTIONS) => {
    const encode = (values, key, operation) => {
        return values.reduce((acc, v) => {
            if (key === "childFilter" && v === "ALL") return acc;

            const encoded = encodeURIComponent(v);

            if (key === "labels") {
                const [labelKey, labelValue] = v.split(":");
                acc[`filters[${key}][${operation}][${labelKey}]`] = encodeURIComponent(labelValue);
            } else {
                const paramKey = `filters[${key}][${operation}]`;
                acc[paramKey] = acc[paramKey] ? `${acc[paramKey]},${encoded}` : encoded;
            }
            return acc;
        }, {});
    };

    return filters.reduce((query, filter) => {
        const match = OPTIONS.find((o) => o.value.label === filter.label);
        const key = match ? match.key : filter.label === "text" ? "q" : null;
        const operation = filter.comparator?.value || match?.comparators?.find(c => c.value === filter.operation)?.value || "$eq";

        if (key) {
            if (key !== "date") {
                Object.assign(query, encode(filter.value, key, operation));
            } else if (filter.value?.length > 0) {
                const {startDate, endDate} = filter.value[0];
                if(startDate && endDate) {
                    query["filters[startDate][$gte]"] = startDate;
                    query["filters[endDate][$lte]"] = endDate;
                }
            }
        }
        return query;
    }, {});
};

export const decodeSearchParams = (query, include, OPTIONS) => {
    const params = Object.entries(query)
        .filter(([key]) => (key.startsWith("filters[") || key === "q") && (!key.startsWith("filters[startDate") && !key.startsWith("filters[endDate")) )
        .map(([key, value]) => {
            const match = key.match(/filters\[(.*?)\]\[(.*?)\](?:\[(.*?)\])?/);

            if (!match) return null;

            const [, field, operation, subKey] = match;

            if (field === "labels" && subKey) {
                return {label: field, value: `${subKey}:${decodeURIComponent(value)}`, operation};
            }

            const label = field === "q" ? "text" : OPTIONS.find(o => o.key === field)?.value.label || field;
            const comparator = OPTIONS.find(o => o.key === field)?.comparators?.find(c => c.value === operation) || {value: operation};

            return {label, value: [decodeURIComponent(value)], operation: comparator.value};
        })
        .filter(Boolean);

    // Handle date filter
    if (query["filters[startDate][$gte]"] && query["filters[endDate][$lte]"]) {
        params.push({
            label: "absolute_date",
            value: [{
                startDate: query["filters[startDate][$gte]"],
                endDate: query["filters[endDate][$lte]"]
            }],
            operation: "$between"
        });
    }

    return params;
};

export const isSearchPath = (name: string) => ["home", "flows/list", "executions/list", "logs/list", "namespaces/update", "admin/triggers"].includes(name);