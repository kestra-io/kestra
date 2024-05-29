package io.kestra.core.models.tasks.runners;

public enum TargetOS {
    LINUX {
        public String getLineSeparator() {
            return "\n";
        }
    },
    WINDOWS {
        public String getLineSeparator() {
            return "\r\n";
        }
    },
    AUTO {
        public String getLineSeparator() {
            return System.lineSeparator();
        }
    };

    public abstract String getLineSeparator();
}
