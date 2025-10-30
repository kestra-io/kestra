import {defineComponent} from "vue";
import type {RouteRecordName, RouteParams} from "vue-router";

export type Schemas = {
    $ref?: string;
    $schema?: string;
    properties?: {
        [key: string]: any;
    };
    definitions?: {
        [key: string]: object;
    };
};

export type Field = {
    component: ReturnType<typeof defineComponent>;
    value: any;
    label: string;
    required?: boolean;
    disabled?: boolean;
};

export type PairField = Omit<Field, "value"> & {
    value: Record<string, string>;
    property: string;
};

type InputField = Field & {
    inputs: any[];
};

type ConcurrencyField = Field & {
    root: string;
    schema: object;
};

type EditorField = Field & {
    navbar: boolean;
    input: boolean;
    lang: string;
    shouldFocus: boolean;
    style: {
        height: string;
    };
};

export type Fields = {
    id: Field;
    namespace: Field;
    description: Field;
    retry: Field;
    labels: PairField;
    inputs: InputField;
    outputs: EditorField;
    variables: PairField;
    concurrency: ConcurrencyField;
    sla: Field;
    disabled: Field;
};

export interface NoCodeElement {
    id: string;
    type: string;
    [key:string]: any;
}

export type CollapseItem = {
    title: string;
    section: string;
    elements?: NoCodeElement[];
    blockSchemaPath: string;
};

export type Breadcrumb = {
    label: string;
    to?: {
        name?: RouteRecordName;
        params?: RouteParams;
    };
    component?: ReturnType<typeof defineComponent>;
    panel?: boolean;
};

export type Component = ReturnType<typeof defineComponent>;

type BasicParams = {
    id: string;
    section: BlockType;
}

type CreationParams = BasicParams & {
    position: "before" | "after";
}

export type TopologyClickParams =
  | { action: "edit"; params: BasicParams }
  | { action: "create"; params: CreationParams };

export type BlockType = "tasks"
    |    "triggers"
    |    "conditions"
    |    "taskRunners"
