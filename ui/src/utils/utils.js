import moment from "moment";

export default class Utils {
    static uid() {
        return String.fromCharCode(Math.floor(Math.random() * 26) + 97) +
            Math.random().toString(16).slice(2) +
            Date.now().toString(16).slice(4);
    }

    static flatten(object) {
        return Object.assign({}, ...function _flatten(child, path = []) {
            return []
                .concat(...Object
                    .keys(child)
                    .map(key => typeof child[key] === "object" ?
                        _flatten(child[key], path.concat([key])) :
                        ({[path.concat([key]).join(".")] : child[key]})
                    )
                );
        }(object));
    }

    static executionVars(data) {
        if (data === undefined) {
            return [];
        }

        const flat = Utils.flatten(data);

        return Object.keys(flat).map(key =>  {
            if (key === "variables.executionId") {
                return {key, value: flat[key], subflow: true};
            }

            if (typeof(flat[key]) === "string") {
                let date = moment(flat[key], moment.ISO_8601);
                if (date.isValid()) {
                    return {key, value: flat[key], date: true};
                }
            }

            if (typeof(flat[key]) === "number") {
                return {key, value: flat[key].toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, "$1 ")};
            }

            return {key, value: flat[key]};

        })
    }
}
