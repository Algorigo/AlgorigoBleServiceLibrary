# AlgorigoBleServiceLibrary

Android 폰을 **BLE 주변기기(peripheral)**로 동작시키는 RxJava3 라이브러리입니다. GATT 서버를 열어 원하는 서비스와 characteristic을 등록하고 광고(advertising)합니다. 중앙기기(central)가 보낸 읽기·쓰기·notification 구독·연결/해제 요청은 Rx 이벤트로 전달됩니다.

- Maven 좌표: `com.algorigo.rx:bleservice`
- 패키지: `com.algorigo.algorigobleservice`
- 최소 Android 버전: 5.0 (API 21)

## 설치

이 라이브러리가 배포된 저장소(`NEXUS_REPOSITORY_URL`, SNAPSHOT은 `NEXUS_SNAPSHOT_REPOSITORY_URL`)를 앱의 `repositories`에 추가한 뒤 의존성을 선언합니다. 배포된 POM에는 의존성이 들어 있지 않으므로 RxJava3 관련 라이브러리도 함께 추가해야 합니다.

```kotlin
dependencies {
    implementation("com.algorigo.rx:bleservice:1.0.1")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")   // RxJava3 포함 (라이브러리 빌드와 같은 의존성)
    implementation("com.jakewharton.rxrelay3:rxrelay:3.0.1")
}
```

라이브러리 소스를 직접 수정하면서 쓰려면 앱의 `settings.gradle.kts`에서 `algorigobleservice/` 디렉터리를 모듈로 포함해 `implementation(project(":algorigobleservice"))`로 사용할 수 있습니다.

### 권한

라이브러리 manifest에는 `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`만 선언되어 있습니다. Android 12(API 31) 이상에서는 앱이 이 두 권한을 런타임에 요청해야 합니다. 광고 권한이 없으면 서버가 `RuntimeException("Permission")`으로 끝납니다.

## 사용 방법

`BleGattDelegate`를 상속해 서비스 구성, 광고 옵션, 요청 처리를 구현하고 `BleGattServiceGenerator.startServer()`로 시작합니다.

```kotlin
@SuppressLint("MissingPermission")
class MyGattDelegate : BleGattDelegate<Any>() {

    override fun getServices(): List<BluetoothGattService> = listOf(
        BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(BleGattServiceGenerator.initWritableCharacteristic(WRITE_UUID))
            addCharacteristic(BleGattServiceGenerator.initNotifyCharacteristic(NOTIFY_UUID))
        },
        BleGattServiceGenerator.initBatteryService(),
    )

    override fun getAdvertiseOption(): BleAdvertiseOption =
        BleAdvertiseOption.Builder()
            .setName("TestBle")      // 폰의 블루투스 이름이 이 값으로 바뀝니다
            .setUuid(SERVICE_UUID)
            .build()

    override fun handleEvent(event: BleGattServiceGenerator.BluetoothServiceEvent) {
        when (event) {
            is BleGattServiceGenerator.BluetoothServiceEvent.CharacteristicReadRequest ->
                event.callback(byteArrayOf(80))              // 응답 값 (반드시 호출)
            is BleGattServiceGenerator.BluetoothServiceEvent.CharacteristicWriteRequest ->
                event.callback(event.value)                  // 응답 (반드시 호출)
            is BleGattServiceGenerator.BluetoothServiceEvent.NotificationStartRequest ->
                event.callback(Observable.interval(1, TimeUnit.SECONDS).map { byteArrayOf(it.toByte()) })
            else -> {}
        }
    }
}

// 구독하는 동안 서버가 열려 있고, 해제하면 연결을 끊고 서버를 닫습니다
val disposable = BleGattServiceGenerator.startServer(context, MyGattDelegate())
    .subscribe({ /* emitEvent() 로 보낸 이벤트 */ }, { /* 에러 */ })
```

delegate 없이 `startServer(context, services, option)`을 호출하면 `BluetoothServiceEvent` 스트림을 직접 받습니다. 이 스트림에는 `ClientConnected` / `ClientDisconnected` 이벤트도 포함됩니다.

### 동작 방식과 주의할 점

- **한 번에 중앙기기 하나만** 다룹니다. 연결되어 있는 동안에는 광고를 멈추고, 연결이 끊기면 다시 광고합니다.
- 읽기·쓰기 이벤트의 `callback`을 호출해야 중앙기기에 응답이 갑니다. 호출하지 않으면 중앙기기 쪽 요청이 타임아웃됩니다.
- 읽기 응답은 offset부터 22바이트씩 보냅니다. 긴 값은 중앙기기가 offset을 바꿔 다시 읽으므로, 그때마다 전체 값을 다시 넘겨야 합니다.
- notification은 중앙기기가 CCCD를 켜면 `NotificationStartRequest`가 오고, 앱이 넘긴 `Observable<ByteArray>`의 값을 차례로 전송합니다. 중앙기기가 끄면 구독이 해제됩니다. 너무 빨리 보내면 전송이 밀리므로 `onNotificationSentObservable`(전송 완료 신호)로 속도를 조절하세요. 샘플 앱의 `util/ObservableThrottle.kt`가 그 예입니다.
- 광고 데이터는 31바이트로 제한됩니다. 이름과 128비트 서비스 UUID를 함께 넣으면 이름은 짧아야 합니다. 초과하면 `RuntimeException("Failure:<code>")`가 납니다.
- `startServer(context, delegate)`는 현재 `DelegateEvent.ClientConnected` / `ClientDisconnected`를 내보내지 않습니다. 연결 상태가 필요하면 `handleEvent`에서 `BluetoothServiceEvent.ClientConnected` / `ClientDisconnected`를 처리하세요.

## 빌드

### 사전 설정

아래 속성을 `~/.gradle/gradle.properties`에 설정하세요. 저장소에 커밋하지 마세요. 배포하지 않는 빌드도 Gradle 설정 단계에서 이 값을 읽으므로, 없으면 빌드가 실패합니다.

```properties
NEXUS_REPOSITORY_URL=...            # master 브랜치 (정식 버전)
NEXUS_SNAPSHOT_REPOSITORY_URL=...   # 그 밖의 브랜치 (SNAPSHOT)
nexusUsername=...
nexusPassword=...
```

배포하려면 서명 키 설정(`signing.*` 속성)도 필요합니다. 샘플 앱(`app`, `test_app`)을 빌드하려면 각 디렉터리에 Firebase `google-services.json`이 있어야 합니다. 이 파일은 저장소에 포함되어 있지 않습니다.

### 명령어

```bash
./gradlew :algorigobleservice:build              # 라이브러리 빌드 (CI 와 동일)
./gradlew :algorigobleservice:assembleRelease    # AAR → algorigobleservice/build/outputs/aar/
./gradlew :app:installDebug                      # 주변기기 샘플 앱 설치
./gradlew :test_app:installDebug                 # 중앙기기 테스트 앱 설치
```

## 배포

버전은 `algorigobleservice/build.gradle.kts`의 `versionStr`입니다. git 브랜치 이름에 `master`가 들어가면 `<versionStr>`로, 아니면 `<versionStr>-<사용자명>-SNAPSHOT`으로 배포됩니다.

```bash
./gradlew :algorigobleservice:assembleRelease :algorigobleservice:publish
```

`publish`는 미리 빌드된 AAR을 올리므로 `assembleRelease`를 먼저 실행해야 합니다.

CircleCI(`.circleci/config.yml`)는 git-flow 브랜치(`master`, `develop`, `feature/*`, `release/*`, `hotfix/*`)에서 라이브러리를 빌드합니다. `master`는 `NEXUS_REPOSITORY_URL`로, `release/*`와 `hotfix/*`는 `NEXUS_SNAPSHOT_REPOSITORY_URL`로 자동 배포합니다.

## 프로젝트 구조

```
algorigobleservice/   배포되는 라이브러리
  BleGattServiceGenerator.kt   GATT 서버, 이벤트 스트림, characteristic 생성 도우미
  BleGattDelegate.kt           서비스 구성과 요청 처리를 묶는 추상 클래스
  BleAdvertiser.kt             광고 옵션(BleAdvertiseOption)과 광고 시작/중지
app/                  주변기기 샘플 앱 (포그라운드 서비스에서 GATT 서버 실행, 배터리 서비스 + notify characteristic)
test_app/             중앙기기 테스트 앱 (algorigoble2 로 샘플 서비스를 스캔해 연결하고 notification 수를 셈)
common_library/       두 샘플 앱이 함께 쓰는 포그라운드 서비스/알림 유틸 (배포하지 않음)
```

`app`을 한 폰에서 실행해 광고를 켜고, 다른 폰에서 `test_app`으로 스캔하면 라이브러리를 실제 기기로 확인할 수 있습니다. 자세한 내부 동작은 [CLAUDE.md](CLAUDE.md)를 참고하세요.
