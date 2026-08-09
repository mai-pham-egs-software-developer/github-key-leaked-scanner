package com.leakscanner.detect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PrivateKeyDetectorTest {

  // 64 hex chars, high entropy, no leading repeated-character run — deliberately not a real key.
  private static final String HEX_A = "0123456789abcdef".repeat(4);
  private static final String HEX_B = "fedcba9876543210".repeat(4);
  private static final String HEX_C = "a1b2c3d4e5f60789".repeat(4);

  private final PrivateKeyDetector detector = new PrivateKeyDetector();

  @Test
  void prefixedHexWithContextKeywordIsHighConfidence() {
    List<KeyMatch> findings = detector.scanLine("PRIVATE_KEY=0x" + HEX_A, "config/.env");

    assertThat(findings).hasSize(1);
    KeyMatch m = findings.get(0);
    assertThat(m.confidence()).isEqualTo(KeyMatch.Confidence.HIGH);
    assertThat(m.hasPrefix()).isTrue();
    assertThat(m.filePath()).isEqualTo("config/.env");
  }

  @Test
  void prefixedHexWithoutContextKeywordIsMediumConfidence() {
    // looks like an ordinary tx/block hash — "txHash" isn't a key-context keyword
    List<KeyMatch> findings = detector.scanLine("txHash: 0x" + HEX_A, null);

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).confidence()).isEqualTo(KeyMatch.Confidence.MEDIUM);
    assertThat(findings.get(0).hasPrefix()).isTrue();
  }

  @Test
  void bareHexWithContextKeywordIsMediumConfidence() {
    List<KeyMatch> findings = detector.scanLine("wallet=" + HEX_A, null);

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).confidence()).isEqualTo(KeyMatch.Confidence.MEDIUM);
    assertThat(findings.get(0).hasPrefix()).isFalse();
  }

  @Test
  void bareHexWithNoContextIsLowConfidence() {
    // the common case: an ordinary SHA-256/commit hash with no key-related context
    List<KeyMatch> findings = detector.scanLine("checksum " + HEX_A, null);

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).confidence()).isEqualTo(KeyMatch.Confidence.LOW);
  }

  @Test
  void redactedValueKeepsOnlyFirst6AndLast4Chars() {
    List<KeyMatch> findings = detector.scanLine("wallet=" + HEX_A, null);

    assertThat(findings.get(0).redacted()).isEqualTo("012345...cdef");
  }

  @Test
  void contextSnippetRedactsTheKeyButKeepsSurroundingText() {
    String line = "some prefix text before it   PRIVATE_KEY=0x" + HEX_A + "   trailing text after it";
    List<KeyMatch> findings = detector.scanLine(line, null);

    String context = findings.get(0).context();
    assertThat(context).doesNotContain(HEX_A);
    assertThat(context).contains("0x012345...cdef");
    assertThat(context).contains("PRIVATE_KEY=");
  }

  @Test
  void allZerosHexIsFilteredAsLowEntropy() {
    List<KeyMatch> findings = detector.scanLine("wallet=0x" + "0".repeat(64), null);

    assertThat(findings).isEmpty();
  }

  @Test
  void repeatedCharacterHexIsFilteredAsLowEntropy() {
    List<KeyMatch> findings = detector.scanLine("wallet=0x" + "a".repeat(64), null);

    assertThat(findings).isEmpty();
  }

  @Test
  void leadingSequentialRunIsFilteredEvenWithHighDistinctCount() {
    String sequentialHex = "1".repeat(11) + HEX_A.substring(11);
    List<KeyMatch> findings = detector.scanLine("wallet=0x" + sequentialHex, null);

    assertThat(findings).isEmpty();
  }

  @Test
  void checksumLinesAreSkippedEntirelyEvenWithKeyContext() {
    List<KeyMatch> findings = detector.scanLine("wallet_checksum=0x" + HEX_A, null);

    assertThat(findings).isEmpty();
  }

  @Test
  void multipleDistinctMatchesOnOneLineAreAllReported() {
    List<KeyMatch> findings =
        detector.scanLine("wallet=0x" + HEX_A + " backup=0x" + HEX_B, null);

    assertThat(findings).hasSize(2);
    assertThat(findings).extracting(KeyMatch::confidence).containsOnly(KeyMatch.Confidence.HIGH);
  }

  @Test
  void scanLineReturnsEmptyWhenNoHexPresent() {
    assertThat(detector.scanLine("just a normal line of code", null)).isEmpty();
  }

  @Test
  void scanPatchReturnsEmptyForNullOrBlankInput() {
    assertThat(detector.scanPatch(null)).isEmpty();
    assertThat(detector.scanPatch("")).isEmpty();
    assertThat(detector.scanPatch("   ")).isEmpty();
  }

  @Test
  void scanPatchOnlyLooksAtAddedLinesNotRemovedOrContextLines() {
    String patch =
        "diff --git a/config/old.env b/config/old.env\n"
            + "index 1234567..89abcde 100644\n"
            + "--- a/config/old.env\n"
            + "+++ b/config/old.env\n"
            + "@@ -1,2 +1,2 @@\n"
            + "-WALLET_KEY=0x" + HEX_B + "\n"
            + "+WALLET_KEY=0x" + HEX_A + "\n"
            + " UNCHANGED_LINE=foo\n";

    List<KeyMatch> findings = detector.scanPatch(patch);

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).confidence()).isEqualTo(KeyMatch.Confidence.HIGH);
    assertThat(findings.get(0).redacted())
        .isEqualTo("012345...cdef");
  }

  @Test
  void scanPatchAttributesFindingsToTheFileFromThePrecedingDiffHeader() {
    String patch =
        "diff --git a/config/old.env b/config/old.env\n"
            + "+++ b/config/old.env\n"
            + "+WALLET_KEY=0x" + HEX_A + "\n"
            + "diff --git a/other/file.txt b/other/file.txt\n"
            + "+++ b/other/file.txt\n"
            + "+plain 0x" + HEX_C + " no context here\n";

    List<KeyMatch> findings = detector.scanPatch(patch);

    assertThat(findings).hasSize(2);
    assertThat(findings.get(0).filePath()).isEqualTo("config/old.env");
    assertThat(findings.get(1).filePath()).isEqualTo("other/file.txt");
    assertThat(findings.get(1).confidence()).isEqualTo(KeyMatch.Confidence.MEDIUM);
  }

  @Test
  void plusPlusPlusFileHeaderLineIsNotTreatedAsAnAddedLine() {
    String patch = "diff --git a/x b/x\n+++ b/x\n";

    assertThat(detector.scanPatch(patch)).isEmpty();
  }

  @Test
  void quickCheck() {
        String patch =
                "diff --git a/config/old.env b/config/old.env\n"
                        + "index 1234567..89abcde 100644\n"
                        + "--- a/config/old.env\n"
                        + "+++ b/config/old.env\n"
                        + "@@ -1,2 +1,2 @@\n"
                        + "-WALLET_KEY=0x" + HEX_B + "\n"
                        + "+WALLET_KEY=0x" + HEX_A + "\n"
                        + " UNCHANGED_LINE=foo\n";

        List<KeyMatch> findings = detector.scanPatch(patch);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).confidence()).isEqualTo(KeyMatch.Confidence.HIGH);
        assertThat(findings.get(0).redacted())
                .isEqualTo("012345...cdef");
    }
}
