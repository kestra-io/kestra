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
                    .map(key => typeof child[key] === 'object' ?
                        _flatten(child[key], path.concat([key])) :
                        ({ [path.concat([key]).join(".")] : child[key] })
                    )
                );
        }(object));
    }

    static executionVars(data) {
        const variables = [];
        if (data !== undefined) {
            const flat = Utils.flatten(data);
            for (const key in flat) {
                let date = moment(flat[key], moment.ISO_8601);

                if (date.isValid()) {
                    variables.push({key, value: date.format('LLLL')});
                } else {
                    variables.push({key, value: flat[key]});
                }
            }
        }

        return variables;
    }
}
