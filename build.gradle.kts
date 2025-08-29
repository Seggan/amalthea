plugins {
    kotlin("jvm") version "2.2.0"
}

group = "io.github.seggan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

val compileIntrinsics by tasks.registering(JavaCompile::class) {
    source = sourceSets.main.get().resources
    include("Intrinsics.java")
    classpath = files()
    sourceCompatibility = "21"
    destinationDirectory = layout.buildDirectory.dir("generated/intrinsics")
}

tasks.processResources {
    dependsOn(compileIntrinsics)
    from(compileIntrinsics) {
        include("Intrinsics.class")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}