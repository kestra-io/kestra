package io.kestra.core.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.dashboards.filters.*;
import io.kestra.core.utils.Enums;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Builder
public record QueryFilter(
    Field field,
    Op operation,
    Object value
) {

    @JsonCreator
    public QueryFilter(
        @JsonProperty("field") Field field,
        @JsonProperty("operation") Op operation,
        @JsonProperty("value") Object value
    ) {
        this.field = field;
        this.operation = operation;
        this.value = value;
    }

    public enum Op {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        LESS_THAN,
        GREATER_THAN_OR_EQUAL_TO,
        LESS_THAN_OR_EQUAL_TO,
        IN,
        NOT_IN,
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS,
        REGEX,
        PREFIX
    }

    @SuppressWarnings("unchecked")
    private List<Object> asValues(Object value) {
        return value instanceof String valueStr ? Arrays.asList(valueStr.split(",")) : (List<Object>) value;
    }

    public <T extends Enum<T>> AbstractFilter<T> toDashboardFilterBuilder(T field, Object value) {
        return switch (this.operation) {
            case EQUALS -> EqualTo.<T>builder().field(field).value(value).build();
            case NOT_EQUALS -> NotEqualTo.<T>builder().field(field).value(value).build();
            case GREATER_THAN -> GreaterThan.<T>builder().field(field).value(value).build();
            case LESS_THAN -> LessThan.<T>builder().field(field).value(value).build();
            case GREATER_THAN_OR_EQUAL_TO -> GreaterThanOrEqualTo.<T>builder().field(field).value(value).build();
            case LESS_THAN_OR_EQUAL_TO -> LessThanOrEqualTo.<T>builder().field(field).value(value).build();
            case IN -> In.<T>builder().field(field).values(asValues(value)).build();
            case NOT_IN -> NotIn.<T>builder().field(field).values(asValues(value)).build();
            case STARTS_WITH -> StartsWith.<T>builder().field(field).value(value.toString()).build();
            case ENDS_WITH -> EndsWith.<T>builder().field(field).value(value.toString()).build();
            case CONTAINS -> Contains.<T>builder().field(field).value(value.toString()).build();
            case REGEX -> Regex.<T>builder().field(field).value(value.toString()).build();
            case PREFIX -> Regex.<T>builder().field(field).value("^" + value.toString().replace(".", "\\.") + "(?:\\..+)?$").build();
        };
    }

    public enum Field {
        QUERY("q") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        SCOPE("scope") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        NAMESPACE("namespace") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.REGEX, Op.IN, Op.NOT_IN, Op.PREFIX);
            }
        },
        KIND("kind") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS,Op.NOT_EQUALS, Op.IN, Op.NOT_IN);
            }
        },
        LABELS("labels") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.IN, Op.NOT_IN, Op.CONTAINS);
            }
        },
        METADATA("metadata") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.IN, Op.NOT_IN, Op.CONTAINS);
            }
        },
        FLOW_ID("flowId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.REGEX, Op.IN, Op.NOT_IN, Op.PREFIX);
            }
        },
        FLOW_REVISION("flowRevision") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.IN, Op.NOT_IN);
            }
        },
        ID("id") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.REGEX);
            }
        },
        ASSET_ID("assetId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.REGEX);
            }
        },
        TYPE("type") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.REGEX);
            }
        },
        CREATED("created") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.GREATER_THAN_OR_EQUAL_TO, Op.GREATER_THAN, Op.LESS_THAN_OR_EQUAL_TO, Op.LESS_THAN, Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        UPDATED("updated") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.GREATER_THAN_OR_EQUAL_TO, Op.GREATER_THAN, Op.LESS_THAN_OR_EQUAL_TO, Op.LESS_THAN, Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        START_DATE("startDate") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.GREATER_THAN_OR_EQUAL_TO, Op.GREATER_THAN, Op.LESS_THAN_OR_EQUAL_TO, Op.LESS_THAN, Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        END_DATE("endDate") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.GREATER_THAN_OR_EQUAL_TO, Op.GREATER_THAN, Op.LESS_THAN_OR_EQUAL_TO, Op.LESS_THAN, Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        STATE("state") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.IN, Op.NOT_IN);
            }
        },
        TIME_RANGE("timeRange") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS);
            }
        },
        TRIGGER_EXECUTION_ID("triggerExecutionId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.IN, Op.NOT_IN);
            }
        },
        TRIGGER_ID("triggerId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.IN, Op.NOT_IN);
            }
        },
        TRIGGER_STATE("triggerState"){
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        EXECUTION_ID("executionId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.IN, Op.NOT_IN);
            }
        },
        TASK_ID("taskId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.IN, Op.NOT_IN);
            }
        },
        TASK_RUN_ID("taskRunId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.IN, Op.NOT_IN);
            }
        },
        CHILD_FILTER("childFilter") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        WORKER_ID("workerId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.IN, Op.NOT_IN);
            }
        },
        EXISTING_ONLY("existingOnly") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        MIN_LEVEL("level") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        PATH("path") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.IN);
            }
        },
        PARENT_PATH("parentPath") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.STARTS_WITH);
            }
        },
        VERSION("version") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        };

        private static final Map<String, Field> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toMap(Field::value, Function.identity()));

        public abstract List<Op> supportedOp();

        private final String value;

        Field(String value) {
            this.value = value;
        }

        @JsonCreator
        public static Field fromString(String value) {
            return Enums.fromString(value, BY_VALUE, "field");
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum Resource {
        FLOW {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.LABELS, Field.NAMESPACE, Field.QUERY, Field.SCOPE, Field.FLOW_ID);
            }
        },
        NAMESPACE {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.EXISTING_ONLY);
            }
        },
        EXECUTION {
            @Override
            public List<Field> supportedField() {
                return List.of(
                    Field.QUERY, Field.SCOPE, Field.FLOW_ID, Field.START_DATE, Field.END_DATE,
                    Field.STATE, Field.LABELS, Field.TRIGGER_EXECUTION_ID, Field.CHILD_FILTER,
                    Field.NAMESPACE, Field.KIND
                );
            }
        },
        LOG {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.QUERY, Field.SCOPE, Field.NAMESPACE, Field.START_DATE,
                    Field.END_DATE, Field.FLOW_ID, Field.TRIGGER_ID, Field.MIN_LEVEL, Field.EXECUTION_ID
                );
            }
        },
        TASK {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.NAMESPACE, Field.QUERY, Field.END_DATE, Field.FLOW_ID, Field.START_DATE,
                    Field.STATE, Field.LABELS, Field.TRIGGER_EXECUTION_ID, Field.CHILD_FILTER
                );
            }
        },
        TEMPLATE {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.NAMESPACE, Field.QUERY);
            }
        },
        TRIGGER {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.QUERY, Field.SCOPE, Field.NAMESPACE, Field.WORKER_ID, Field.FLOW_ID,
                    Field.START_DATE, Field.END_DATE, Field.TRIGGER_ID, Field.TRIGGER_STATE
                );
            }
        },
        SECRET_METADATA {
            @Override
            public List<Field> supportedField() {
                return List.of(
                    Field.QUERY,
                    Field.NAMESPACE
                );
            }
        },
        KV_METADATA {
            @Override
            public List<Field> supportedField() {
                return List.of(
                    Field.QUERY,
                    Field.NAMESPACE,
                    Field.UPDATED
                );
            }
        },
        NAMESPACE_FILE_METADATA {
            @Override
            public List<Field> supportedField() {
                return List.of(
                    Field.QUERY,
                    Field.NAMESPACE,
                    Field.PATH,
                    Field.PARENT_PATH,
                    Field.VERSION,
                    Field.UPDATED
                );
            }
        },
        ASSET {
            @Override
            public List<Field> supportedField() {
                return List.of(
                    Field.QUERY,
                    Field.ID,
                    Field.TYPE,
                    Field.NAMESPACE,
                    Field.METADATA,
                    Field.UPDATED
                );
            }
        },
        ASSET_USAGE {
            @Override
            public List<Field> supportedField() {
                return List.of(
                    Field.ASSET_ID,
                    Field.NAMESPACE,
                    Field.FLOW_ID,
                    Field.FLOW_REVISION,
                    Field.EXECUTION_ID,
                    Field.TASK_ID,
                    Field.TASK_RUN_ID,
                    Field.CREATED
                );
            }
        },
        ASSET_LINEAGE_EVENT {
            @Override
            public List<Field> supportedField() {
                return List.of(
                    Field.ASSET_ID,
                    Field.NAMESPACE,
                    Field.FLOW_ID,
                    Field.FLOW_REVISION,
                    Field.EXECUTION_ID,
                    Field.TASK_ID,
                    Field.TASK_RUN_ID,
                    Field.CREATED
                );
            }
        };

        public abstract List<Field> supportedField();

        /**
         * Converts {@code Resource} enums to a list of {@code ResourceField},
         * including fields and their supported operations.
         *
         * @return List of {@code ResourceField} with resource names, fields, and operations.
         */

        private static FieldOp toFieldInfo(Field field) {
            List<Operation> operations = field.supportedOp().stream()
                .map(Resource::toOperation)
                .toList();
            return new FieldOp(field.name().toLowerCase(), field.value(), operations);
        }

        private static Operation toOperation(Op op) {
            return new Operation(op.name(), op.name());
        }
    }

    public record FieldOp(String name, String value, List<Operation> operations) {
    }

    public record Operation(String name, String value) {
    }

    public static void validateQueryFilters(List<QueryFilter> filters, Resource resource){
        if (filters == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        filters.forEach(filter -> {
            if (!filter.field().supportedOp().contains(filter.operation())) {
                errors.add("Operation %s is not supported for field %s. Supported operations are %s".formatted(
                    filter.operation(), filter.field().name(),
                    filter.field().supportedOp().stream().map(Op::name).collect(Collectors.joining(", "))));
            }
            if (!resource.supportedField().contains(filter.field())){
                errors.add("Field %s is not supported for resource %s. Supported fields are %s".formatted(
                    filter.field().name(), resource.name(),
                    resource.supportedField().stream().map(Field::name).collect(Collectors.joining(", "))));
            }
        });
        if (!errors.isEmpty()){
            throw new InvalidQueryFiltersException(errors);
        }
    }

}
