package pubsub.reporter

import java.util.concurrent.TimeUnit.SECONDS

class ConsoleReporter(name: String)
    extends RateReporter(
      SECONDS.toNanos(1),
      (
          messagesPerSec: Double,
          bytesPerSec: Double,
          totalMessages: Long,
          totalBytes: Long
      ) => {
        println(
          name +
            f": $messagesPerSec%,.0f msgs/sec, $bytesPerSec%,.0f bytes/sec, " +
            f"$totalMessages%,d total messages ${totalBytes / (1024 * 1024)}%,d MB total payload"
        )
      }
    ) {}
