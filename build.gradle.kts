plugins {
    kotlin("jvm") version "1.8.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.slack.api:slack-api-client:1.44.2")
    implementation("com.slack.api:bolt-socket-mode:1.27.3")
    implementation("com.slack.api:bolt:1.27.3")
    implementation("org.kohsuke:github-api:1.314")
    implementation("org.gitlab4j:gitlab4j-api:6.0.0-rc.7")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("GitPrSlackReminder")
}