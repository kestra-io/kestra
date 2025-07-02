import {YamlAutoCompletion} from "../../services/autoCompletionProvider";
import {Store} from "vuex";

export class TestSuitesAutoCompletion extends YamlAutoCompletion {
    private _store: Store<Record<string, any>>;
    constructor(store: Store<Record<string, any>>) {
        super();
        this._store = store;
    }
    async rootFieldAutoCompletion(): Promise<string[]> {
        return [
            ...(await super.rootFieldAutoCompletion()),
        ];
    }
}
