package com.leakscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scanner")
public class ScannerProperties {

  private String dataDir = "./data";
  private double sampleRate = 1.0;
  private int concurrency = 6;
  private int requestDelayMs = 150;
  private int maxRetries = 3;
  private final Scheduled scheduled = new Scheduled();
  private final Telegram telegram = new Telegram();

  public String getDataDir() {
    return dataDir;
  }

  public void setDataDir(String dataDir) {
    this.dataDir = dataDir;
  }

  public double getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(double sampleRate) {
    this.sampleRate = sampleRate;
  }

  public int getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(int concurrency) {
    this.concurrency = concurrency;
  }

  public int getRequestDelayMs() {
    return requestDelayMs;
  }

  public void setRequestDelayMs(int requestDelayMs) {
    this.requestDelayMs = requestDelayMs;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Scheduled getScheduled() {
    return scheduled;
  }

  public Telegram getTelegram() {
    return telegram;
  }

  /** Bot token and chat id must come from env (TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID) — never commit them. */
  public static class Telegram {
    private String botToken;
    private String chatId;

    public String getBotToken() {
      return botToken;
    }

    public void setBotToken(String botToken) {
      this.botToken = botToken;
    }

    public String getChatId() {
      return chatId;
    }

    public void setChatId(String chatId) {
      this.chatId = chatId;
    }
  }

  public static class Scheduled {
    private boolean enabled = false;
    private String cron = "0 5 * * * *";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getCron() {
      return cron;
    }

    public void setCron(String cron) {
      this.cron = cron;
    }
  }
}
