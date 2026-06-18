# Developer API

SmartSpawner provides a clean Java API that lets you interact with spawners, listen to events, and modify behavior from your own plugins.

## Quick Navigation

<CardGrid>

<DocCard icon="📦" title="Installation" link="/docs/developer-api/installation" desc="Add SmartSpawner as a dependency via JitPack (Maven / Gradle)." />

<DocCard icon="🔧" title="API Creation" link="/docs/developer-api/creation" desc="Get the API instance and initialize your integration." />

<DocCard icon="📊" title="Data Access" link="/docs/developer-api/data-access" desc="Read and modify spawner data: stack size, storage, XP, etc." />

<DocCard icon="📡" title="Events" link="/docs/developer-api/events" desc="Listen to spawner lifecycle events in your plugin." />

<DocCard icon="🎨" title="GUI Layout API" link="/docs/developer-api/gui-layout" desc="Register and inject custom GUI layout providers." />

<DocCard icon="✅" title="Validation" link="/docs/developer-api/validation" desc="Validate spawner data and entity types." />

<DocCard icon="💡" title="Examples" link="/docs/developer-api/examples" desc="Complete working examples to get started quickly." />

</CardGrid>

## Overview

The API is available via JitPack and follows a provider pattern:

```java
SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
```

The API gives you access to:
- Reading and modifying spawner properties (stack size, delay, range)
- Accessing and modifying spawner storage contents
- Creating and removing spawners programmatically
- Listening to spawner events (place, break, generate, sell, etc.)
- Registering custom GUI layout providers
- Validating entity types and spawner data
