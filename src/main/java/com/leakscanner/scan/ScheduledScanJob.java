package com.leakscanner.scan;

import com.leakscanner.config.ScannerProperties;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional recurring trigger — disabled by default (scanner.scheduled.enabled=false).
 * When enabled, scans the UTC hour that just completed once per tick of the configured cron.
 */
@Component
public class ScheduledScanJob {

  private static final Logger log = LoggerFactory.getLogger(ScheduledScanJob.class);
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final ScanService scanService;
  private final ScannerProperties props;

  public ScheduledScanJob(ScanService scanService, ScannerProperties props) {
    this.scanService = scanService;
    this.props = props;
  }

  @Scheduled(cron = "${scanner.scheduled.cron}")
  public void run() {
//    if (!props.getScheduled().isEnabled()) return;

    ZonedDateTime completedHour = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);
    String date = completedHour.format(DATE_FMT);
    int hour = completedHour.getHour();

    try {
      var stats = scanService.scanHour(date, hour, null, null);
      log.info(
          "Scheduled scan {}: pushEvents={} sampled={} high={} medium={} low={}",
          stats.hourKey(),
          stats.pushEvents(),
          stats.commitsSampled(),
          stats.matchesHigh(),
          stats.matchesMedium(),
          stats.matchesLow());
    } catch (Exception e) {
      log.warn("Scheduled scan failed for {}-{}", date, hour, e);
    }
  }
}
