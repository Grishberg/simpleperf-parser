import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.google.protobuf.gradle.*
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf

plugins {
    kotlin("jvm") version "1.7.10"
    application
    id("com.google.protobuf") version "0.8.19"
}

sourceSets{
    create("sample"){
        proto {
            srcDir("src/sample/protobuf")
        }
    }
}
group = "com.github.grishberg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation ("com.google.protobuf:protobuf-java:3.6.1")
    implementation ("com.google.protobuf:protobuf-gradle-plugin:0.8.19")

    testImplementation("junit:junit:4.12")
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
}

application {
    mainClass.set("MainKt")
}

java.sourceSets["main"].java {
    srcDir("build/generated/source/proto/main/java")
}