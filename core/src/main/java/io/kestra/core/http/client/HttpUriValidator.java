
package io.kestra.core.http.client;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.runners.DefaultRunContext;

public final class HttpUriValidator {

    private static final Pattern CIDR_ENTRY = Pattern.compile("^(?:(https?|wss?)://)?([^/]+)/(\\d{1,3})$");

    private HttpUriValidator() {
    }

    private record ResolvedAuthority(String host, int port) {
    }

    private record Cidr(InetAddress network, int prefixLength, String scheme) {

        static Cidr parsed(String entry) {
            Matcher matcher = CIDR_ENTRY.matcher(entry);

            if (!matcher.matches()) {
                return null;
            }

            Optional<InetAddress> network = parseIpLiteral(matcher.group(2));

            if (network.isEmpty()) {
                return null;
            }

            int prefixLength;

            try {
                prefixLength = Integer.parseInt(matcher.group(3));
            } catch (NumberFormatException e) {
                return null;
            }

            if (prefixLength > network.get().getAddress().length * 8) {
                return null;
            }

            return new Cidr(network.get(), prefixLength, matcher.group(1));
        }

        boolean contains(String host) {
            return parseIpLiteral(host).map(this::contains).orElse(false);
        }

        boolean contains(InetAddress address) {
            byte[] addressBytes = address.getAddress();
            byte[] networkBytes = network.getAddress();

            if (addressBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addressBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            int remainingBits = prefixLength % 8;

            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainingBits);

            return (addressBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        }
    }

    @SuppressWarnings("unchecked")
    public static void validate(URI uri, DefaultRunContext runContext) {
        List<String> allowedList = (List<String>) runContext
            .getTaskProperty("kestra.tasks.http.allowed-list", List.class)
            .orElse(Collections.emptyList());

        List<String> deniedList = (List<String>) runContext
            .getTaskProperty("kestra.tasks.http.denied-list", List.class)
            .orElse(Collections.emptyList());

        if (allowedList.isEmpty() && deniedList.isEmpty()) {
            return;
        }

        ResolvedAuthority authority = resolveAuthority(uri);

        if (authority == null) {
            throw new IllegalArgumentException(
                "The URI %s has no resolvable host to check against the configured allow/deny lists (kestra.tasks.http.allowed-list / kestra.tasks.http.denied-list)."
                    .formatted(uri)
            );
        }

        if (
            !allowedList.isEmpty()
                && allowedList.stream().noneMatch(entry -> isListEntryMatch(entry, uri))
        ) {
            throw new IllegalArgumentException(
                "The URI %s is not in the configured allowed list (kestra.tasks.http.allowed-list)."
                    .formatted(uri)
            );
        }

        if (deniedList.stream().anyMatch(entry -> isListEntryMatch(entry, uri))) {
            throw new IllegalArgumentException(
                "The URI %s is in the configured denied list (kestra.tasks.http.denied-list)."
                    .formatted(uri)
            );
        }

        checkResolvedAddresses(authority.host(), deniedList);
    }

    private static ResolvedAuthority resolveAuthority(URI uri) {
        if (uri.getHost() != null) {
            return new ResolvedAuthority(uri.getHost(), uri.getPort());
        }

        String authority = uri.getAuthority();

        if (authority == null) {
            return null;
        }

        String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);

        if (hostAndPort.startsWith("[")) {
            int closingBracket = hostAndPort.indexOf(']');

            if (closingBracket == -1) {
                return null;
            }

            String host = hostAndPort.substring(1, closingBracket);
            String remainder = hostAndPort.substring(closingBracket + 1);

            if (remainder.startsWith(":")) {
                try {
                    return new ResolvedAuthority(
                        host,
                        Integer.parseInt(remainder.substring(1))
                    );
                } catch (NumberFormatException e) {
                    return new ResolvedAuthority(host, -1);
                }
            }

            return new ResolvedAuthority(host, -1);
        }

        int colon = hostAndPort.lastIndexOf(':');

        if (colon == -1) {
            return new ResolvedAuthority(hostAndPort, -1);
        }

        try {
            return new ResolvedAuthority(
                hostAndPort.substring(0, colon),
                Integer.parseInt(hostAndPort.substring(colon + 1))
            );
        } catch (NumberFormatException e) {
            return new ResolvedAuthority(hostAndPort, -1);
        }
    }

    private static Optional<InetAddress> parseIpLiteral(String host) {
        String candidate = stripTrailingDot(host);

        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        int zoneIndex = candidate.indexOf('%');

        if (zoneIndex != -1 && candidate.indexOf(':') != -1) {
            candidate = candidate.substring(0, zoneIndex);
        }

        try {
            return Optional.of(InetAddress.ofLiteral(candidate));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String canonicalHost(String host) {
        return parseIpLiteral(host)
            .map(InetAddress::getHostAddress)
            .orElseGet(() -> stripTrailingDot(host).toLowerCase(Locale.ROOT));
    }

    private static boolean isListEntryMatch(String entry, URI uri) {
        ResolvedAuthority requestAuthority = resolveAuthority(uri);

        if (requestAuthority == null) {
            return false;
        }

        Cidr cidr = Cidr.parsed(entry);

        if (cidr != null) {
            return cidr.contains(requestAuthority.host())
                && (cidr.scheme() == null
                    || cidr.scheme().equalsIgnoreCase(uri.getScheme()));
        }

        boolean matchSubdomains = entry.startsWith("*.") || entry.contains("://*.");

        String strippedEntry = entry
            .replace("://*.", "://")
            .replaceFirst("^\\*\\.", "");

        URI entryUri;

        try {
            entryUri = URI.create(
                strippedEntry.contains("://")
                    ? strippedEntry
                    : "//" + strippedEntry
            );
        } catch (IllegalArgumentException e) {
            return false;
        }

        ResolvedAuthority entryAuthority = resolveAuthority(entryUri);

        if (entryAuthority == null) {
            return false;
        }

        String host = canonicalHost(requestAuthority.host());
        String entryHost = canonicalHost(entryAuthority.host());

        boolean hostMatches = matchSubdomains
            && parseIpLiteral(host).isEmpty()
                ? host.endsWith("." + entryHost)
                : host.equals(entryHost);

        if (!hostMatches) {
            return false;
        }

        if (
            entryUri.getScheme() != null
                && !entryUri.getScheme().equalsIgnoreCase(uri.getScheme())
        ) {
            return false;
        }

        if (
            entryAuthority.port() != -1
                && entryAuthority.port() != defaultedPort(uri.getScheme(), requestAuthority.port())
        ) {
            return false;
        }

        String entryPath = entryUri.getPath();

        if (entryPath != null && !entryPath.isEmpty() && !"/".equals(entryPath)) {
            String path = uri.getPath() == null ? "" : uri.getPath();

            if (!path.startsWith(entryPath)) {
                return false;
            }
        }

        return true;
    }

    private static void checkResolvedAddresses(
        String host,
        List<String> deniedList) {
        if (deniedList.isEmpty() || parseIpLiteral(host).isPresent()) {
            return;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);

            for (InetAddress address : addresses) {
                boolean denied = deniedList.stream()
                    .anyMatch(entry -> isDeniedAddress(entry, address));

                if (denied) {
                    throw new IllegalArgumentException(
                        "The host '%s' resolves to the address '%s', which is in the configured denied list (kestra.tasks.http.denied-list)."
                            .formatted(host, address.getHostAddress())
                    );
                }
            }
        } catch (UnknownHostException e) {
            // The proxy or the WebSocket client's resolver may resolve this hostname.
            // Hostname-based allow/deny checks have already been performed.
        }
    }

    private static boolean isDeniedAddress(String entry, InetAddress address) {
        Cidr cidr = Cidr.parsed(entry);

        if (cidr != null) {
            return cidr.contains(address);
        }

        String strippedEntry = entry
            .replace("://*.", "://")
            .replaceFirst("^\\*\\.", "");

        try {
            URI entryUri = URI.create(
                strippedEntry.contains("://")
                    ? strippedEntry
                    : "//" + strippedEntry
            );

            ResolvedAuthority authority = resolveAuthority(entryUri);

            return authority != null
                && parseIpLiteral(authority.host())
                    .map(address::equals)
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String stripTrailingDot(String host) {
        return host.endsWith(".")
            ? host.substring(0, host.length() - 1)
            : host;
    }

    private static int defaultedPort(String scheme, int port) {
        if (port != -1) {
            return port;
        }

        return switch (scheme == null ? "" : scheme.toLowerCase(Locale.ROOT)) {
            case "http", "ws" -> 80;
            case "https", "wss" -> 443;
            default -> -1;
        };
    }
}
