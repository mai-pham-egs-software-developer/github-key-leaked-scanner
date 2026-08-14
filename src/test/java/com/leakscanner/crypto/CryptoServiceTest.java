package com.leakscanner.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link CryptoService#isIgnoredFilePath}, the filter that skips RPC balance checks
 * for matches found in package manifests/lockfiles (npm, Maven, pip, Cargo, Go, ...), since
 * their hex-ish hashes routinely look like private keys but never are.
 */
class CryptoServiceTest {

  @Test
  void ignoresWellKnownManifestAndLockfileNames() {
    assertThat(CryptoService.isIgnoredFilePath("package.json")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("package-lock.json")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("yarn.lock")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("pnpm-lock.yaml")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("pom.xml")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("requirements.txt")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("uv.lock")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("poetry.lock")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("Cargo.lock")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("go.sum")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("composer.lock")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("Gemfile.lock")).isTrue();
  }

  @Test
  void matchesOnTheFilenameRegardlessOfDirectory() {
    assertThat(CryptoService.isIgnoredFilePath("frontend/app/package.json")).isTrue();
    assertThat(CryptoService.isIgnoredFilePath("services/api/go.sum")).isTrue();
  }

  @Test
  void doesNotIgnoreRegularSourceFiles() {
    assertThat(CryptoService.isIgnoredFilePath("src/main/java/App.java")).isFalse();
    assertThat(CryptoService.isIgnoredFilePath(".env")).isFalse();
    assertThat(CryptoService.isIgnoredFilePath("config/secrets.yaml")).isFalse();
  }

  @Test
  void doesNotIgnoreFilesThatMerelyContainAManifestNameAsASubstring() {
    assertThat(CryptoService.isIgnoredFilePath("my-package.json.bak")).isFalse();
    assertThat(CryptoService.isIgnoredFilePath("not-a-real-pom.xml.txt")).isFalse();
  }

  @Test
  void handlesNullFilePath() {
    assertThat(CryptoService.isIgnoredFilePath(null)).isFalse();
  }
}
