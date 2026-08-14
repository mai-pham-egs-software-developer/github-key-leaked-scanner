package com.leakscanner.notify;

import com.leakscanner.config.ScannerProperties;
import com.leakscanner.crypto.dto.BalanceResultDto;
import com.leakscanner.detect.KeyMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Sends a Telegram alert when a leaked key turns out to hold a nonzero balance.
 * Never includes the private key itself — only chain, address, balance and file path.
 */
@Component
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScannerProperties.Telegram config;

    public TelegramNotifier(ScannerProperties props) {
        this.config = props.getTelegram();
    }

    public void sendFundedKeyAlert(BalanceResultDto result, KeyMatch keyMatch) {
        if (config.getBotToken() == null || config.getBotToken().isBlank()
                || config.getChatId() == null || config.getChatId().isBlank()) {
            log.warn("Funded key found ({} {} balance={}) but Telegram isn't configured "
                            + "(scanner.telegram.bot-token / chat-id)",
                    result.getChain(), result.getAddress(), result.getBalance());
            return;
        }

        String text =
             """ 
                🚨 Funded leaked key found
                chain: %s
                address: %s
                balance: %s
                file: %s
              """.formatted(result.getChain(), result.getAddress(),
                     result.getBalance(), keyMatch.filePath());
        send(text);
    }

    private void send(String text) {
        try {
            String url = "https://api.telegram.org/bot" + config.getBotToken() + "/sendMessage";
            String body = "chat_id=" + URLEncoder.encode(config.getChatId(), StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Telegram sendMessage failed: HTTP {} {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Telegram sendMessage failed: {}", e.getMessage());
        }
    }
}
