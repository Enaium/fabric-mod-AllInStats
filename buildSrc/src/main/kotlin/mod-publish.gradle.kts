import me.modmuss50.mpp.PublishModTask
import org.gradle.util.internal.VersionNumber

plugins {
    id("me.modmuss50.mod-publish-plugin")
}

afterEvaluate {
    publishMods {
        val disableObfuscation = findProperty("fabric.loom.disableObfuscation")?.toString()?.toBoolean() ?: false
        val minecraftVersion = property("minecraft.version").toString()
        val modern = VersionNumber.parse(minecraftVersion) >= VersionNumber.parse("1.14")
        file = tasks.named<AbstractArchiveTask>(if (disableObfuscation) "jar" else "remapJar").get().archiveFile.get()
        type = STABLE
        displayName = "AllInStats ${project.version}"
        changelog = rootProject.file("changelog.md").readText(Charsets.UTF_8)
        modLoaders.add("fabric")

        curseforge {
            projectId = "1714594"
            accessToken = providers.gradleProperty("curseforge.token")
            minecraftVersions.add(minecraftVersion)
            client = true
            server = true
            requires("fabric-language-kotlin", "fabric-orm-jimmer", "fabric-database-h2", if (modern) "fabric-api" else "legacy-fabric-api")
            optional("fabric-gui-imgui")
        }

        modrinth {
            projectId = "xpGiIHCK"
            accessToken = providers.gradleProperty("modrinth.token")
            minecraftVersions.add(minecraftVersion)
            requires("fabric-language-kotlin", "fabric-orm-jimmer", "fabric-database-h2", if (modern) "fabric-api" else "legacy-fabric-api")
            optional("fabric-gui-imgui")
        }

        github {
            repository = "Enaium/fabric-mod-AllInStats"
            accessToken = providers.gradleProperty("github.token")
            commitish = "main"
        }

        tasks.withType<PublishModTask>().configureEach {
            dependsOn(tasks.named(if (disableObfuscation) "jar" else "remapJar"))
        }
    }
}
