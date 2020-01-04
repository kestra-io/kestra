# Plugins path default to pwd & must be exported as env var
KESTRA_PLUGINS_PATH=${KESTRA_PLUGINS_PATH:-"$(dirname "$0")/plugins"}
export KESTRA_PLUGINS_PATH=${KESTRA_PLUGINS_PATH}

# Check java version
JAVA_FULLVERSION=$(java -fullversion 2>&1)
case "$JAVA_FULLVERSION" in
    [a-z]*\ full\ version\ \"\(1|9|10\)\..*\")
        echo "[ERROR] Kestra require at least Java 11." 1>&2
        exit 1
        ;;
esac

# Exec
exec java ${JAVA_OPTS} -cp "$0:${KESTRA_PLUGINS_PATH}/*" org.kestra.cli.App "$@"
exit 127