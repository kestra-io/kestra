# Security Policy

## Supported Versions

We provide security updates for:

- The `latest` release
- All actively supported LTS versions (see [release policy](https://kestra.io/docs/releases) for the current list)

Unsupported versions won't receive patches, please upgrade to a supported one before reporting an issue tied to it.

## Reporting a Vulnerability

Please report security vulnerabilities through **GitHub Security Advisories**, not public issues or PRs:

👉 [Report a vulnerability](https://github.com/kestra-io/kestra/security/advisories/new)

This opens a private draft advisory visible only to maintainers, so details stay confidential until we're ready to disclose.

If you're unable to use GitHub for any reason, you can email us instead at **security@kestra.io**, but the GitHub flow is preferred since it keeps everything in one place.

### What to include

- A clear description of the vulnerability and its impact
- Steps to reproduce (PoC code or a minimal repro if possible)
- Affected version(s) and component(s)
- Your assessment of severity, if you have one (CVSS vector is welcome but not required)

### Guidelines

- Please don't disclose the issue publicly (blog, social media, public issue, etc.) until we've published the advisory or given you the go-ahead.
- Give us a reasonable amount of time to investigate and patch before disclosure. We aim to move fast, see our commitment below.
- If you're not sure whether something is a security issue, report it privately anyway, we'd rather triage a false positive than miss a real one.

## Our Commitment

- We'll acknowledge new reports within **2 business days**.
- We'll keep you updated as we validate, fix, and prepare a release.
- Once a fix is released, we coordinate disclosure with you: affected customers are notified directly before the advisory goes public, and we credit you in the published advisory (or keep you anonymous, your call).

## Acknowledgments

We're happy to publicly credit anyone who reports a vulnerability responsibly, in the advisory itself and/or release notes. Let us know in your report if you'd prefer to stay anonymous instead.

Thanks for helping keep Kestra secure.