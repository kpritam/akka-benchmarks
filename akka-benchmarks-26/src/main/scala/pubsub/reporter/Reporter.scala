package pubsub.reporter

trait Reporter {
  def onReport(
      messagesPerSec: Double,
      bytesPerSec: Double,
      totalMessages: Long,
      totalBytes: Long
  ): Unit
}
