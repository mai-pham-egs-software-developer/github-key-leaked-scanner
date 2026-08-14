package com.leakscanner.crypto;

import com.leakscanner.crypto.chain.IChain;
import com.leakscanner.crypto.dto.BalanceResultDto;
import com.leakscanner.detect.KeyMatch;
import com.leakscanner.notify.TelegramNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;


@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    /**
     * Package-manager manifests/lockfiles are full of long hex-ish hashes (npm integrity
     * hashes, go.sum checksums, Cargo/Poetry/uv lock hashes, ...) that match the private-key
     * regex but are never actually secrets, so checking their balance only burns RPC calls.
     */
    private static final Set<String> IGNORED_FILENAMES = Set.of(
            "package.json", "package-lock.json", "npm-shrinkwrap.json", "yarn.lock", "pnpm-lock.yaml",
            "pom.xml", "build.gradle", "build.gradle.kts", "gradle.lockfile",
            "requirements.txt", "Pipfile", "Pipfile.lock", "poetry.lock", "uv.lock",
            "Cargo.toml", "Cargo.lock",
            "go.mod", "go.sum",
            "composer.json", "composer.lock",
            "Gemfile", "Gemfile.lock",
            "mix.exs", "mix.lock",
            "pubspec.yaml", "pubspec.lock",
            "paket.lock");

    @Autowired
    private List<IChain> chains;

    @Autowired
    private TelegramNotifier telegramNotifier;

    /** {@code privateKeyHex} is used only for this call — never logged, stored, or attached to any result. */
    public void process(String privateKeyHex, KeyMatch keyMatch) {
      if (isIgnoredFilePath(keyMatch.filePath())) {
          log.debug("Skipping balance check for {} — package manifest/lockfile, not a real key source",
                  keyMatch.filePath());
          return;
      }

      for (IChain chain:chains) {
          BalanceResultDto result;
          try {
              result = chain.retrieve(privateKeyHex);
          } catch (RuntimeException e) {
              log.warn("Balance check failed for {} candidate in {}: {}",
                      chain.chain(), keyMatch.filePath(), e.getMessage());
              continue;
          }

          log.info("Balance check: chain={} address={} balance={} file={}",
                  result.getChain(), result.getAddress(), result.getBalance(), keyMatch.filePath());

          if (result.getBalance() != null && result.getBalance().compareTo(BigDecimal.ZERO) > 0) {
              telegramNotifier.sendFundedKeyAlert(result, keyMatch);
          }
      }
    }

    static boolean isIgnoredFilePath(String filePath) {
        if (filePath == null) {
            return false;
        }
        int lastSlash = filePath.lastIndexOf('/');
        String filename = lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
        return IGNORED_FILENAMES.contains(filename);
    }

}
