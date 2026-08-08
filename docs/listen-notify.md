# PostgreSQL Listen / Notify

Octavius wraps PostgreSQL's `LISTEN` / `NOTIFY` mechanism in a fully asynchronous, coroutine-based API, so you can build applications that react to database events as they happen. It isn't a bad mental model to picture the *cursus publicus* — Rome's state courier network:
channels are the roads, notifications are the dispatches, and your listener is the officer waiting at the way-station for the next rider.

Everything here lives behind `session.notifications`, an instance of `NotificationManager`.

## Subscribing and Emitting

### Listening to Channels
Register the connection on one or more channels with `listen`:
```kotlin
session.notifications.listen("senatus_curia", "province_updates")
```

### Unlistening
Drop a specific channel, or every subscription on the current connection:
```kotlin
session.notifications.unlisten("senatus_curia")
session.notifications.unlistenAll()
```

### Emitting Notifications
Dispatch a message to a channel with `notify`, optionally carrying a text payload:
```kotlin
session.notifications.notify("province_updates", "consul_elected=42")
```

## Receiving Notifications

Incoming dispatches arrive on a `SharedFlow<PgNotification>`, collected like any other coroutine flow:

```kotlin
coroutineScope.launch {
    session.notifications.messages.collect { notification ->
        println("Channel: ${notification.channel}")
        println("Payload: ${notification.payload}")
        println("Sent by backend PID: ${notification.processId}")
    }
}
```

> [!NOTE]
> **Buffer capacity.** The `messages` flow sits on a buffer sized by `notificationBufferCapacity` (default **256**) — think of it as how many riders the way-station can hold before it runs out of room. Once full, the driver discards the oldest, not-yet-processed notification to make space for the newest one (a **DROP_OLDEST** strategy). Adjust the capacity through the connection properties or the JDBC URL (`?notificationBufferCapacity=N`).

## Listener Loops

Subscribing alone doesn't pull anything off the wire — the driver has to actively read the socket to feed the `messages` flow. Octavius offers two suspendable loops for this; launch one of them in a coroutine right after subscribing.

### 1. Polling Listener Loop
```kotlin
session.notifications.startPollingListenerLoop(pollTimeoutMs = 500)
```
This is the sentry doing rounds: it checks the socket on a timeout instead of staying fixed at one post.
- **Graceful cancellation** — cancelling the coroutine simply ends the rounds; the connection itself stays open.
- **Reusable** — since the connection survives, it can go straight back into a pool or be reused for further queries.
- **Best for** applications where connections are pooled or shared across responsibilities.

### 2. Interruptible Listener Loop
```kotlin
session.notifications.startInterruptibleListenerLoop()
```
This is the sentry standing fixed watch: it blocks indefinitely on the socket read and doesn't budge until something arrives.
- **Hard cancellation** — because the underlying socket read blocks hard, cancelling the coroutine forces the driver to abort the physical connection to break out of it.
- **Not reusable** — the connection is closed for good after cancellation.
- **Best for** dedicated, long-lived background workers, where paying for polling isn't worth it and a disposable connection on shutdown is fine.
