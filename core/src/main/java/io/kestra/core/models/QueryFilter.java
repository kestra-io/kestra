package io.kestra.core.models;

import io.kestra.core.utils.Enums;
import lombok.Builder;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Builder
public record QueryFilter(
    Field field,
    Op operation,
    Object value
) {
    public enum Op {
        EQUALS("$eq"),
        NOT_EQUALS("$ne"),
        GREATER_THAN("$gte"),
        LESS_THAN("$lte"),
        IN("$in"),
        NOT_IN("$notIn"),
        STARTS_WITH("$startsWith"),
        ENDS_WITH("$endsWith"),
        CONTAINS("$contains"),
        REGEX("$regex");

        private static final Map<String, Op> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toMap(Op::value, Function.identity()));

        private final String value;

        Op(String value) {
            this.value = value;
        }

        public static Op fromString(String value) {
            return Enums.fromString(value, BY_VALUE, "operation");
        }

        public String value() {
            return value;
        }
    }

    public enum Field {
        QUERY("q") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.REGEX);
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
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH, Op.ENDS_WITH, Op.REGEX);
            }
        },
        LABELS("labels") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        FLOW_ID("flowId") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.IN, Op.NOT_IN);
            }
        },
        START_DATE("startDate") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.GREATER_THAN, Op.LESS_THAN, Op.EQUALS, Op.NOT_EQUALS);
            }
        },
        END_DATE("endDate") {
            @Override
            public List<Op> supportedOp() {
                return List.of(Op.GREATER_THAN, Op.LESS_THAN, Op.EQUALS, Op.NOT_EQUALS);
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
                return List.of(Op.EQUALS, Op.NOT_EQUALS, Op.CONTAINS, Op.STARTS_WITH,
                    Op.ENDS_WITH, Op.IN, Op.NOT_IN, Op.REGEX);
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
        };

        private static final Map<String, Field> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toMap(Field::value, Function.identity()));

        public abstract List<Op> supportedOp();

        private final String value;

        Field(String value) {
            this.value = value;
        }

        public static Field fromString(String value) {
            return Enums.fromString(value, BY_VALUE, "field");
        }

        public String value() {
            return value;
        }
    }


    public enum Resource {
        FLOW {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.LABELS, Field.NAMESPACE, Field.QUERY, Field.SCOPE);
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
                    Field.QUERY, Field.SCOPE, Field.FLOW_ID, Field.START_DATE, Field.END_DATE, Field.TIME_RANGE,
                    Field.STATE, Field.LABELS, Field.TRIGGER_EXECUTION_ID, Field.CHILD_FILTER,
                    Field.NAMESPACE
                );
            }
        },
        LOG {
            @Override
            public List<Field> supportedField() {
                return List.of(Field.NAMESPACE, Field.START_DATE, Field.END_DATE,
                    Field.FLOW_ID, Field.TRIGGER_ID, Field.MIN_LEVEL
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
                return List.of(Field.QUERY, Field.NAMESPACE, Field.WORKER_ID, Field.FLOW_ID
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
        public static List<ResourceField> asResourceList() {
            return Arrays.stream(values())
                .map(Resource::toResourceField)
                .toList();
        }

        private static ResourceField toResourceField(Resource resource) {
            List<FieldOp> fieldOps = resource.supportedField().stream()
                .map(Resource::toFieldInfo)
                .toList();
            return new ResourceField(resource.name().toLowerCase(), fieldOps);
        }

        private static FieldOp toFieldInfo(Field field) {
            List<Operation> operations = field.supportedOp().stream()
                .map(Resource::toOperation)
                .toList();
            return new FieldOp(field.name().toLowerCase(), field.value(), operations);
        }

        private static Operation toOperation(Op op) {
            return new Operation(op.name(), op.value());
        }
    }

    public record ResourceField(String name, List<FieldOp> fields) {}
    public record FieldOp(String name, String value, List<Operation> operations) {}
    public record Operation(String name, String value) {}

}
