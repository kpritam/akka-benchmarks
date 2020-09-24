package pubsub.reporter

import java.util.concurrent.locks.LockSupport

class RateReporter(reportIntervalNs: Long, reporter: Reporter)
    extends Runnable {

  private val parkNs = reportIntervalNs

  private var halt = false
  private var totalBytes = 0L
  private var totalMessages = 0L
  private var lastTotalBytes = 0L
  private var lastTotalMessages = 0L
  private var lastTimestamp = System.nanoTime

  override def run(): Unit = {
    do {
      LockSupport.parkNanos(parkNs)
      val currentTotalMessages = totalMessages
      val currentTotalBytes = totalBytes
      val currentTimestamp = System.nanoTime
      val timeSpanNs = currentTimestamp - lastTimestamp
      val messagesPerSec =
        ((currentTotalMessages - lastTotalMessages) * reportIntervalNs) / timeSpanNs.toDouble
      val bytesPerSec =
        ((currentTotalBytes - lastTotalBytes) * reportIntervalNs) / timeSpanNs.toDouble
      reporter.onReport(
        messagesPerSec,
        bytesPerSec,
        currentTotalMessages,
        currentTotalBytes
      )
      lastTotalBytes = currentTotalBytes
      lastTotalMessages = currentTotalMessages
      lastTimestamp = currentTimestamp
    } while (!halt)
  }

  def stop(): Unit = {
    halt = true
  }

  def onMessage(messages: Long, bytes: Long): Unit = {
    totalBytes += bytes
    totalMessages += messages
  }

}
