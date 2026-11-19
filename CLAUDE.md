# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android library (Kotlin, RxJava3) that turns the phone into a **BLE peripheral**: it opens a `BluetoothGattServer`, registers caller-supplied `BluetoothGattService`s, advertises, and surfaces client requests (read, write, notification subscribe, connect/disconnect) as an Rx event stream. The library module `algorigobleservice` is published as `com.algorigo.rx:bleservice` (version `versionStr` in `algorigobleservice/build.gradle.kts`) with `maven-publish` to the Maven repository named by the `NEXUS_REPOSITORY_URL` / `NEXUS_SNAPSHOT_REPOSITORY_URL` Gradle properties. The URLs are not in this repo; the CI step calls it "Upload Maven Central". Consumers either depend on that artifact or include `algorigobleservice/` as a local Gradle module. There are no real tests (only the generated `Example*Test` stubs).

Modules (`settings.gradle.kts`):
- `algorigobleservice/` — the published library (minSdk 21, compileSdk 34, Java/JVM 1.8, Kotlin 1.8.10, AGP 8.1.1).
- `app/` — Compose sample **peripheral**: a foreground `BluetoothService` that toggles a GATT server via `MyGattDelegate` (custom service with write + notify characteristics, plus the standard battery service backed by `BatteryManager`).
- `test_app/` — Compose sample **central** that scans for the sample's service UUID and connects with `com.algorigo.rx:algorigoble2:2.3.1` (the `LOCAL_ALGORIGO_BLE` switch in its build file is commented out). Use the two apps on two phones to exercise the library end to end.
- `common_library/` — unpublished helper used only by the two apps: `AbsForegroundService` (starts foreground with a "Stop" action that calls `exitProcess(0)`) and `NotificationUtil`.

## Commands

```bash
./gradlew :algorigobleservice:build              # what CI runs (assemble + lint + stub unit tests)
./gradlew :algorigobleservice:assembleRelease    # build the AAR
./gradlew :app:installDebug                      # peripheral sample (needs app/google-services.json)
./gradlew :test_app:installDebug                 # central sample (needs test_app/google-services.json)
./gradlew :algorigobleservice:assembleRelease :algorigobleservice:publish
```

### Build configuration gotchas
- **Every Gradle invocation needs Nexus properties**, even builds that never publish: the `publishing` block runs at configuration time and does `findProperty(...) as String` for `nexusUsername`, `nexusPassword`, and either `NEXUS_SNAPSHOT_REPOSITORY_URL` (branch name without `master`) or `NEXUS_REPOSITORY_URL` (branch name containing `master`). Put them in `~/.gradle/gradle.properties`; CI downloads that file from `$GRADLE_PROPERTY_PATH`.
- The version is `<versionStr>-<user.name>-SNAPSHOT` unless the current git branch name contains `master`.
- The publication attaches the **pre-built** `build/outputs/aar/algorigobleservice-release.aar` (the `artifact(...)` call sits inside `pom {}` but resolves to the publication). Run `assembleRelease` before `publish`. There is no `from(components...)`, so **the generated POM declares no dependencies**: consumers must add RxJava3, RxRelay3, and androidx.core themselves (the library code imports only those; RxKotlin/RxAndroid are declared but unused).
- Publishing is signed (`signing` plugin); CI fetches the key ring to `algorigobleservice/secret-keys.gpg` from `$SECRET_KEY_RING_PATH`.
- `app` and `test_app` apply the `google-services` and Crashlytics plugins, but `google-services.json` is not tracked. That is why CI builds only `:algorigobleservice`.

### Branching / CI (CircleCI, `.circleci/config.yml`)
Git-flow: `develop`, `master`, `feature/*`, `release/*`, `hotfix/*`. `build` runs on all of them; `deploy` (`./gradlew publish`, reusing the AAR from the build job's workspace) runs on `master` (release version → `NEXUS_REPOSITORY_URL`) and `release/*` / `hotfix/*` (SNAPSHOT). Unlike the newer Algorigo libraries, there is no staging-deploy zip or Central Portal upload. `publish` pushes straight to the `NEXUS_*` URL. Commit messages are in Korean.

## Architecture

The whole library is three files in `com.algorigo.algorigobleservice`.

**`BleGattServiceGenerator`** owns one GATT server session per subscription. `startServer(context, services, option)` returns a cold `Observable<BluetoothServiceEvent>` (subscribed on `Schedulers.io()`). On subscribe, it:
1. opens the GATT server and caches the adapter `address`,
2. adds the services **one at a time**, waiting for each `onServiceAdded` (Android rejects concurrent `addService`),
3. switches on an internal `stateRelay`: `WaitForConnect` starts advertising, and `Connected(device)` disposes the advertiser. Advertising therefore stops while a client is connected and restarts after it disconnects.
4. `buffer(2, 1)` over the connection states turns NoDevice↔Connection transitions into `ClientConnected` / `ClientDisconnected` events.

Disposing the subscription cancels the client connection and closes the server. The model is **single-client**: there is one `connectedDevice`, and a second central connecting replaces it silently with no event.

Client requests are pushed from `BluetoothGattServerCallback` (a binder thread) into `eventRelay` as events that carry a **response callback the app must invoke**. If the app never calls it, the central's request times out.
- `CharacteristicReadRequest.callback(ByteArray)` sends `value[offset, offset+22)`. Long reads arrive as repeated requests with increasing offsets, and each time the app must supply the *full* value again.
- `CharacteristicWriteRequest.callback(ByteArray)` echoes the given bytes as the response. It is sent even when `responseNeeded` is false. Prepared (long) writes are not handled (`onExecuteWrite` is not overridden).
- `NotificationStartRequest` fires when the central writes the CCCD. The app passes an `Observable<ByteArray>` to `callback`, and the library subscribes and calls `notifyCharacteristicChanged` per item (using the API 33 overload when available). If the central asks for a type the characteristic doesn't declare, the library falls back to the one it does declare. A CCCD write of "disable" disposes the stream, and the stream's errors are swallowed (`onErrorComplete`). `onNotificationSentObservable` ticks on **every** `onNotificationSent` (not per characteristic). The sample app uses it with its own `util/ObservableThrottle` to avoid overrunning the stack. That throttle lives in `app`, not in the library.
- CCCD reads are answered from the library's `notificationMap` (keyed by characteristic UUID, not per device).

**`BleGattDelegate<T>`** is the higher-level API: subclass it to supply `getServices()`, `getAdvertiseOption()`, and `handleEvent()`, and emit app-defined events with `emitEvent(T)`. `startServer(context, delegate)` runs `handleEvent` for each event and returns `delegate.getEventObservable()` merged with the server's completion/error. **Known bug:** that overload calls `handleEvent`, not `handleEventOuter`, so `DelegateEvent.ClientConnected` / `ClientDisconnected` are never emitted (the sample `BluetoothService` connection history depends on them). Only `DelegateEvent.Event` values from `emitEvent` arrive.

**`BleAdvertiser`** (internal) plus the public `BleAdvertiseOption.Builder` (`setName`, `setUuid`). It uses legacy advertising: low-power mode, connectable, no timeout, medium TX power, and includes the device name only if a name was set. Note that `setName` **renames the phone's Bluetooth adapter system-wide** (then sleeps 500 ms) and never restores it. The name and a 128-bit service UUID must fit in the 31-byte legacy payload; otherwise `onStartFailure` surfaces as `RuntimeException("Failure:<code>")`. A missing `BLUETOOTH_ADVERTISE` on API 31+ surfaces as `RuntimeException("Permission")`.

Characteristic helpers in the companion (`initReadableCharacteristic`, `initWritableCharacteristic`, `initNotifyCharacteristic`, `initIndicateCharacteristic`, `initBatteryService`) build single-property characteristics. The notify/indicate ones add a CCCD (`0x2902`) descriptor, and the server's CCCD handling depends on that descriptor existing.

## Conventions

- The library manifest declares only `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT`. Runtime permission requests are the app's job (the samples use accompanist-permissions). The public entry points are annotated `@RequiresPermission(BLUETOOTH_CONNECT)`, and callers in the samples use `@SuppressLint("MissingPermission")`.
- The library has no logging and no custom exception types. Failures are plain `RuntimeException`s or the RxJava error from the platform call.
- The sample service/characteristic UUIDs (`6E40000x-B5A3-F393-E0A9-0123456789AB`) are duplicated in `app/.../MyGattDelegate.kt` and `test_app/.../TestBle.kt`. Change both together.
