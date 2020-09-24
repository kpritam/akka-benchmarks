package pubsub.subscription

sealed trait SubscriptionMode
case object RateAdapterMode extends SubscriptionMode
case object RateLimiterMode extends SubscriptionMode
