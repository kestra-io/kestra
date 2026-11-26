package io.kestra.core.http.client;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.utils.ListUtils;

import io.micronaut.core.annotation.Nullable;

public final class UriAccessGuard {
    private UriAccessGuard() {
    }

    public record Violation(Reason reason, String target, String address) {
        public enum Reason {
            NO_HOST,
            NOT_ALLOWED,
            DENIED,
            DENIED_ADDRESS
        }

        public String message() {
            return switch (reason) {
                case NO_HOST -> "The URI %s has no resolvable host.".formatted(target);
                case NOT_ALLOWED -> "The URI %s is not in the allowed list.".formatted(target);
                case DENIED -> "The URI %s is in the denied list.".formatted(target);
                case DENIED_ADDRESS -> "The host '%s' resolves to the address '%s', which is in the denied list.".formatted(target, address);
            };
        }
    }

    /**
     * Returns why the URI is rejected by the lists, or empty when it is allowed. Both lists empty means no check.
     * A host name is also resolved, and rejected when any of its addresses is in the denied list.
     */
    public static Optional<String> findViolation(URI uri, List<String> allowedList, List<String> deniedList) {
        Optional<Violation> violation = checkUri(uri, allowedList, deniedList);
        if (violation.isPresent() || allowedList.isEmpty() && deniedList.isEmpty()) {
            return violation.map(Violation::message);
        }

        String host = resolveAuthority(uri).host();
        if (parseIpLiteral(host).isPresent()) {
            return Optional.empty();
        }

        try {
            return checkAddresses(host, InetAddress.getAllByName(host), deniedList).map(Violation::message);
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    /**
     * A null configuration, or null lists, count as empty lists.
     */
    public static Optional<String> findViolation(String uri, @Nullable UriAccessLists lists) {
        if (lists == null) {
            return findViolation(uri, List.of(), List.of());
        }

        return findViolation(uri, ListUtils.emptyOnNull(lists.allowedList()), ListUtils.emptyOnNull(lists.deniedList()));
    }

    public static Optional<String> findViolation(String uri, List<String> allowedList, List<String> deniedList) {
        try {
            return findViolation(URI.create(uri), allowedList, deniedList);
        } catch (IllegalArgumentException e) {
            return Optional.of("The URI is not valid.");
        }
    }

    /**
     * Checks the URI against the lists without any DNS resolution. Both lists empty means no check.
     */
    public static Optional<Violation> checkUri(URI uri, List<String> allowedList, List<String> deniedList) {
        if (allowedList.isEmpty() && deniedList.isEmpty()) {
            return Optional.empty();
        }

        if (resolveAuthority(uri) == null) {
            return Optional.of(new Violation(Violation.Reason.NO_HOST, uri.toString(), null));
        }

        if (!allowedList.isEmpty() && allowedList.stream().noneMatch(entry -> isListEntryMatch(entry, uri))) {
            return Optional.of(new Violation(Violation.Reason.NOT_ALLOWED, uri.toString(), null));
        }

        if (deniedList.stream().anyMatch(entry -> isListEntryMatch(entry, uri))) {
            return Optional.of(new Violation(Violation.Reason.DENIED, uri.toString(), null));
        }

        return Optional.empty();
    }

    public static Optional<Violation> checkAddresses(String host, InetAddress[] addresses, List<String> deniedList) {
        for (InetAddress address : addresses) {
            if (deniedList.stream().anyMatch(entry -> isDeniedAddress(entry, address))) {
                return Optional.of(new Violation(Violation.Reason.DENIED_ADDRESS, host, address.getHostAddress()));
            }
        }

        return Optional.empty();
    }

    /**
     * Matches a {@code network/prefixLength} allow/deny-list entry, e.g. {@code 169.254.0.0/16}.
     */
    static final Pattern CIDR_ENTRY = Pattern.compile("^(?:(https?)://)?([^/]+)/(\\d{1,3})$");

    /**
     * The host and raw port ({@code -1} when none was given, same convention as {@link URI#getPort()}) Kestra is
     * actually about to connect to, as resolved by {@link #resolveAuthority(URI)}.
     */
    record ResolvedAuthority(String host, int port) {
    }

    /**
     * Resolves the host and port of the URI Kestra is actually about to connect to, falling back to a manual
     * parse of the raw authority when {@link URI#getHost()} returns {@code null}. That happens for an authority
     * containing an underscore, or a partial dotted-decimal IPv4 literal such as {@code 169.254.43518}: strings
     * {@code java.net.URI}'s hostname grammar rejects, but that Apache HttpClient still connects to verbatim via
     * {@code URIUtils.extractHost}. Returns {@code null} only when the URI carries no authority at all.
     */
    static ResolvedAuthority resolveAuthority(URI uri) {
        if (uri.getHost() != null) {
            return new ResolvedAuthority(uri.getHost(), uri.getPort());
        }

        String authority = uri.getAuthority();
        if (authority == null) {
            return null;
        }

        String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
        int colon = hostAndPort.lastIndexOf(':');
        if (colon == -1) {
            return new ResolvedAuthority(hostAndPort, -1);
        }

        try {
            return new ResolvedAuthority(hostAndPort.substring(0, colon), Integer.parseInt(hostAndPort.substring(colon + 1)));
        } catch (NumberFormatException e) {
            return new ResolvedAuthority(hostAndPort, -1);
        }
    }

    /**
     * Parses {@code host} as an IP literal, stripping IPv6 brackets and a trailing dot first. Never resolves a
     * hostname: {@link InetAddress#ofLiteral} parses syntax only and never performs a DNS lookup, so this is safe
     * to call on every allow/deny comparison.
     */
    static Optional<InetAddress> parseIpLiteral(String host) {
        String candidate = stripTrailingDot(host);
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        // ofLiteral rejects an interface-name zone id such as "%lo", yet the JDK resolver still connects to it.
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

    /**
     * Returns the canonical dotted-quad / compressed-IPv6 form when {@code host} is any textual IP literal —
     * decimal, partial dotted-decimal, hex, or IPv4-mapped IPv6 — so that every spelling of the same address
     * matches the same allow/deny entry. Falls back to a lowercased, trailing-dot-stripped hostname otherwise.
     */
    static String canonicalHost(String host) {
        return parseIpLiteral(host)
            .map(InetAddress::getHostAddress)
            .orElseGet(() -> stripTrailingDot(host).toLowerCase(Locale.ROOT));
    }

    /**
     * A {@code network/prefixLength} allow/deny-list entry, e.g. {@code 169.254.0.0/16}, so an entire range can
     * be denied without enumerating every textual spelling of every address in it.
     */
    record Cidr(InetAddress network, int prefixLength, String scheme) {
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

    /**
     * Matches a {@code kestra.tasks.http.allowed-list} / {@code denied-list} entry against the URI Kestra is
     * actually about to connect to. Matching is done on the resolved authority (host, and scheme/port when the
     * entry specifies them) rather than on the raw URI string, so that URL-encoded userinfo
     * (e.g. {@code https://api.trusted.com@169.254.169.254/}, whose host is {@code 169.254.169.254}) or a
     * subdomain suffix (e.g. {@code https://api.trusted.com.attacker.example/}) cannot impersonate an entry
     * they merely start with. An IP-literal host is compared on its canonical form, so {@code 2852039166} and
     * {@code 169.254.169.254} match the same entry.
     * <p>
     * An entry matches its host exactly. Write it as {@code *.api.trusted.com} to also match every subdomain of
     * {@code api.trusted.com} (not {@code api.trusted.com} itself, which needs its own entry), or as a
     * {@code network/prefixLength} CIDR range (e.g. {@code 169.254.0.0/16}) to match every address in it.
     */
    static boolean isListEntryMatch(String entry, URI uri) {
        ResolvedAuthority requestAuthority = resolveAuthority(uri);
        if (requestAuthority == null) {
            return false;
        }

        Cidr cidr = Cidr.parsed(entry);
        if (cidr != null) {
            return cidr.contains(requestAuthority.host())
                && (cidr.scheme() == null || cidr.scheme().equalsIgnoreCase(uri.getScheme()));
        }

        boolean matchSubdomains = entry.startsWith("*.") || entry.contains("://*.");
        String strippedEntry = entry.replace("://*.", "://").replaceFirst("^\\*\\.", "");

        URI entryUri;
        try {
            entryUri = URI.create(strippedEntry.contains("://") ? strippedEntry : "//" + strippedEntry);
        } catch (IllegalArgumentException e) {
            return false;
        }

        ResolvedAuthority entryAuthority = resolveAuthority(entryUri);
        if (entryAuthority == null) {
            return false;
        }

        String host = canonicalHost(requestAuthority.host());
        String lowerEntryHost = canonicalHost(entryAuthority.host());
        boolean hostMatches = matchSubdomains && parseIpLiteral(host).isEmpty()
            ? host.endsWith("." + lowerEntryHost)
            : host.equals(lowerEntryHost);
        if (!hostMatches) {
            return false;
        }

        if (entryUri.getScheme() != null && !entryUri.getScheme().equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        if (entryAuthority.port() != -1 && entryAuthority.port() != defaultedPort(uri.getScheme(), requestAuthority.port())) {
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

    /**
     * Only CIDR and IP literal entries can match an address, and their scheme, port and path are ignored since a name is resolved without them.
     */
    static boolean isDeniedAddress(String entry, InetAddress address) {
        Cidr cidr = Cidr.parsed(entry);
        if (cidr != null) {
            return cidr.contains(address);
        }

        String strippedEntry = entry.replace("://*.", "://").replaceFirst("^\\*\\.", "");
        try {
            ResolvedAuthority entryAuthority = resolveAuthority(URI.create(strippedEntry.contains("://") ? strippedEntry : "//" + strippedEntry));
            return entryAuthority != null && parseIpLiteral(entryAuthority.host()).map(address::equals).orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Strips a single trailing dot from a hostname, e.g. {@code dangerous-url.com.}, since DNS resolves it
     * identically to {@code dangerous-url.com} and it would otherwise fail both the exact and suffix match.
     */
    static String stripTrailingDot(String host) {
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    /**
     * Returns {@code port}, or the scheme's default port when {@code port} is {@code -1}, so that an
     * allow/deny-list entry with an explicit default port still matches a request that omits it.
     */
    static int defaultedPort(String scheme, int port) {
        if (port != -1) {
            return port;
        }

        return switch (scheme == null ? "" : scheme.toLowerCase(Locale.ROOT)) {
            case "https" -> 443;
            case "http" -> 80;
            default -> -1;
        };
    }
}
