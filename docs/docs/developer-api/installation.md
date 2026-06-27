# API Installation

Add SmartSpawner as a dependency via [JitPack](https://jitpack.io/#OpenVdra/SmartSpawner/).

> **Latest version:** [![Latest Release](https://img.shields.io/github/v/release/OpenVdra/SmartSpawner?label=version)](https://github.com/OpenVdra/SmartSpawner/releases/latest)

## Maven

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.OpenVdra</groupId>
    <artifactId>SmartSpawner</artifactId>
    <version>LATEST</version>
    <scope>provided</scope>
</dependency>
```

## Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.OpenVdra:SmartSpawner:LATEST'
}
```

::: tip
Replace `LATEST` with a specific version tag for production builds (e.g. `v3.2.1`).
:::

## plugin.yml

Declare SmartSpawner as a dependency so it loads before your plugin:

```yaml
name: YourPlugin
version: 1.0.0
main: com.yourpackage.YourPlugin
depend: [SmartSpawner]
# — or for optional integration —
softdepend: [SmartSpawner]
```

## Basic Setup

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerProvider;

public class YourPlugin extends JavaPlugin {

    private SmartSpawnerAPI api;

    @Override
    public void onEnable() {
        api = SmartSpawnerProvider.getAPI();
        if (api == null) {
            getLogger().warning("SmartSpawner not found — disabling integration.");
            return;
        }
        getLogger().info("SmartSpawner API connected.");
    }

    public SmartSpawnerAPI getAPI() {
        return api;
    }
}
```
