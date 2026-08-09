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


@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    @Autowired
    private List<IChain> chains;

    @Autowired
    private TelegramNotifier telegramNotifier;

    /** {@code privateKeyHex} is used only for this call — never logged, stored, or attached to any result. */
    public void process(String privateKeyHex, KeyMatch keyMatch) {
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

}
