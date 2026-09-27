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
        // modrinth rejects the fabric loader for the versions before the loader existed
        modLoaders.add(if (modern) "fabric" else "legacy-fabric")

        curseforge {
            projectId = "1714594"
            accessToken = providers.gradleProperty("curseforge.token")
            minecraftVersions.add(minecraftVersion)
            client = true
            requires(
                "fabric-language-kotlin",
                "fabric-orm-jimmer",
                "fabric-database-h2",
                "fabric-gui-imgui",
                if (modern) "fabric-api" else "legacy-fabric-api",
            )
        }

        modrinth {
            projectId = "xpGiIHCK"
            accessToken = providers.gradleProperty("modrinth.token")
            minecraftVersions.add(minecraftVersion)
            environment = CLIENT_ONLY
            requires(
                "fabric-language-kotlin",
                "fabric-orm-jimmer",
                "fabric-database-h2",
                "fabric-gui-imgui",
                if (modern) "fabric-api" else "legacy-fabric-api",
            )
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
