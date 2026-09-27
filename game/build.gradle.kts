plugins {
    id("cn.enaium.fabric-multi-game")
}

fmg {
    common.set(project(":core"))
}

subprojects {
    apply(plugin = "mod-publish")

    repositories {
        maven("https://jitpack.io")
    }

    val minecraftVersion = findProperty("minecraft.version").toString()
    val disableObfuscation = findProperty("fabric.loom.disableObfuscation")?.toString()?.toBoolean() ?: false

    dependencies.add(
        if (disableObfuscation) "implementation" else "modImplementation",
        "cn.enaium:fabric-gui-imgui:${minecraftVersion}+"
    )
}
