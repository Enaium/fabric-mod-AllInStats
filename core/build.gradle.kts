plugins {
    kotlin("jvm")
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.fabric.kotlin)
    implementation(libs.jimmer)
    implementation(libs.h2)
    ksp(libs.jimmer.ksp)
    implementation(libs.jackson)
    implementation(libs.imgui.java.binding)
    implementation(libs.imgui.java.natives.windows)
    implementation(libs.imgui.java.natives.linux)
    implementation(libs.imgui.java.natives.macos)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.h2.driver)
    testImplementation(libs.imgui.java.app)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    javaLauncher.set(
        project.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    )
}

kotlin {
    jvmToolchain(8)
}

/**
 * Renders the interface without Minecraft and writes screenshots to `core/build/reports/allinstats`.
 */
tasks.register<JavaExec>("screenshot") {
    group = "verification"
    description = "Renders the AllInStats interface and writes screenshots"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("cn.enaium.allinstats.gui.StatsGuiScreenshotKt")
    javaLauncher.set(
        project.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    )
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }

}
