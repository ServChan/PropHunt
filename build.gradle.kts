plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "org.lts"
version = "1.0.0"
description = "PropHunt"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi = "io.papermc.paper:paper-api:26.3.build.140-beta"

dependencies {
    compileOnly(paperApi)

    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveFileName.set("PropHunt-${project.version}.jar")
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("26.3")
    }
}
