class Kestra < Formula
  desc "Event-driven orchestration and scheduling platform"
  homepage "https://kestra.io"
  url "https://github.com/kestra-io/kestra/releases/download/v2.0.7/kestra-2.0.7"
  sha256 "a70c858d4d8aabe9532c92ea5c5c6982b77312286b846adc47e6c7eb969326b3"
  license "Apache-2.0"

  depends_on "openjdk@25"

  def install
    libexec.install "kestra-2.0.7" => "kestra"
    # Release assets lose their executable bit on download.
    chmod 0555, libexec/"kestra"
    # openjdk@25 is keg-only, so it is not on PATH: point the launcher at it.
    (bin/"kestra").write_env_script libexec/"kestra", JAVA_HOME: Formula["openjdk@25"].opt_prefix
  end

  test do
    assert_match version.to_s, shell_output("#{bin}/kestra --version")
  end
end
