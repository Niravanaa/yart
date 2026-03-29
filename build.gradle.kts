plugins {
    java
    jacoco
}

group = "com.yart"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks {
    val coverageExcludes = listOf(
        "com/yart/command/**",
        "com/yart/control/**",
        "com/yart/YetAnotherRayTracerPlugin*",
        "com/yart/render/BlockScreen*",
        "com/yart/render/RaytraceSession*",
        "com/yart/render/BlockPaletteLoader*",
        "com/yart/render/RayTracer*"
    )

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    jar {
        archiveBaseName.set("YetAnotherRayTracer")
    }

    test {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(test)
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) {
                    exclude(coverageExcludes)
                }
            })
        )
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    jacocoTestCoverageVerification {
        dependsOn(test)
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) {
                    exclude(coverageExcludes)
                }
            })
        )
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
    }

    register("prCoverageCheck") {
        group = "verification"
        description = "Runs tests and enforces JaCoCo coverage for pull requests."
        dependsOn(test)
        dependsOn(jacocoTestReport)
        dependsOn(jacocoTestCoverageVerification)
    }

    register("lint") {
        group = "verification"
        description = "Runs strict Java compiler lint checks."
        dependsOn(compileJava)
    }

    check {
        setDependsOn(listOf("lint"))
    }
}
