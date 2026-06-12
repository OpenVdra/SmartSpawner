---
title: API Installation
description: Getting started with SmartSpawner API integration.
---
## Getting Started

### Installation

Add SmartSpawner API via [JitPack](https://jitpack.io/#NighterDevelopment/SmartSpawner/)

> **Latest Version:** [![Latest Release](https://img.shields.io/github/v/release/NighterDevelopment/SmartSpawner?label=version)](https://github.com/NighterDevelopment/SmartSpawner/releases/latest)

**Maven:**
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.NighterDevelopment</groupId>
    <artifactId>SmartSpawner</artifactId>
    <version>LATEST</version>
    <scope>provided</scope>
</dependency>
```

**Gradle:**
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.NighterDevelopment:SmartSpawner:LATEST'
}
```

> **Note:** Replace `LATEST` with the specific version number for production builds.

### Plugin Configuration

Add SmartSpawner as dependency in `plugin.yml`:

```yaml
name: YourPlugin
version: 1.0.0
main: com.yourpackage.YourPlugin
depend: [SmartSpawner]
# or
softdepend: [SmartSpawner]  # Optional dependency
```

## API Usage

### Basic Setup

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerProvider;

public class YourPlugin extends JavaPlugin {
    
    private SmartSpawnerAPI api;
    
    @Override
    public void onEnable() {
        // Initialize API
        api = SmartSpawnerProvider.getAPI();
        if (api == null) {
            getLogger().warning("SmartSpawner not found!");
            return;
        }
        
        getLogger().info("SmartSpawner API connected successfully!");
    }
    
    public SmartSpawnerAPI getAPI() {
        return api;
    }
}
```

<br>
<br>

---

*Last update: November 17, 2025 11:38:36*
