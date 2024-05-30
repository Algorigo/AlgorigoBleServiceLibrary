plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

val versionStr = "1.0.1"

fun String.runCommand(workingDir: File = file("./")): String {
    val parts = this.split("\\s".toRegex())
    val proc = ProcessBuilder(*parts.toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    proc.waitFor(1, TimeUnit.MINUTES)
    return proc.inputStream.bufferedReader().readText().trim()
}

fun getCurrentGitBranch(): String {
    var gitBranch = "Unknown branch"
    try {
        val workingDir = File("${project.projectDir}")
        val result = "git rev-parse --abbrev-ref HEAD".runCommand(workingDir)
        gitBranch = result.trim()
    } catch (e: Exception) {
    }
    return gitBranch
}

fun getUserName(): String {
    return System.getProperty("user.name")
}

val group = "com.algorigo.rx"
val archivesBaseName = "bleservice"

val versionName = if (getCurrentGitBranch().contains("master")) {
    versionStr
} else {
    "${versionStr}-${getUserName()}-SNAPSHOT"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = group
            artifactId = archivesBaseName
            version = versionName
            pom {
                name.set("Algorigo Ble Service")
                description.set("Algorigo Ble Service Library")
                url.set("https://github.com/Algorigo/AlgorigoBleServiceLibrary")
                artifact("$buildDir/outputs/aar/${project.name}-release.aar")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("rouddy")
                        name.set("Rouddy Yoo")
                        email.set("rouddy@naver.com")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/Algorigo/AlgorigoBleServiceLibrary")
                    developerConnection.set("scm:git:https://github.com/Algorigo/AlgorigoBleServiceLibrary")
                    url.set("https://github.com/Algorigo/AlgorigoBleServiceLibrary")
                }
            }
        }
    }
    repositories {
        maven {
            url = uri(if (versionName.endsWith("SNAPSHOT")) {
                findProperty("NEXUS_SNAPSHOT_REPOSITORY_URL") as String
            } else {
                findProperty("NEXUS_REPOSITORY_URL") as String
            })
            credentials {
                username = findProperty("nexusUsername") as String
                password = findProperty("nexusPassword") as String
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

android {
    namespace = "com.algorigo.algorigobleservice"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // ReactiveX
    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.0")
    // RxRelay
    implementation("com.jakewharton.rxrelay3:rxrelay:3.0.1")
}