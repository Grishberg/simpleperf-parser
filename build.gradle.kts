import com.google.protobuf.gradle.proto
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
    kotlin("jvm") version "1.7.10"
    application
    id("com.google.protobuf") version "0.8.19"
    id("java")
}

sourceSets {
    create("sample") {
        proto {
            srcDir("src/sample/protobuf")
        }
    }
}
group = "com.github.grishberg"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("com.google.protobuf:protobuf-java:3.6.1")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.19")
    implementation("com.github.grishberg:mvtrace-dependencies:1.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation(kotlin("test"))
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:3.6.1"
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

application {
    mainClass.set("MainKt")
}

java.sourceSets["main"].java {
    srcDir("build/generated/source/proto/main/java")
}

configurations.forEach {
    if (it.name.toLowerCase().contains("proto")) {
        it.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "java-runtime"))
    }
}
