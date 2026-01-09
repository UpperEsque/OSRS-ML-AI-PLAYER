# OSRS ML AI Player

A RuneLite plugin for Machine Learning AI experiments in Old School RuneScape.

## Project Structure

```
OSRS-ML-AI-PLAYER/
├── build.gradle                 # Gradle build configuration
├── settings.gradle              # Project settings
├── runelite-plugin.properties   # Plugin metadata (CRITICAL!)
├── gradle/wrapper/              # Gradle wrapper
└── src/
    ├── main/java/com/osrsml/
    │   ├── OSRSMLPlugin.java    # Main plugin class
    │   ├── OSRSMLConfig.java    # Configuration interface
    │   └── OSRSMLOverlay.java   # Overlay display
    └── test/java/com/osrsml/
        └── OSRSMLPluginTest.java # Test runner
```

## Prerequisites

- **Java 11** (Eclipse Temurin/AdoptOpenJDK recommended)
- **IntelliJ IDEA** (Community or Ultimate)
- **Git**

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/UpperEsque/OSRS-ML-AI-PLAYER.git
cd OSRS-ML-AI-PLAYER
```

### 2. Open in IntelliJ IDEA

1. Open IntelliJ IDEA
2. Click `File > Open`
3. Select the `OSRS-ML-AI-PLAYER` folder
4. Click "Trust Project" when prompted
5. Wait for Gradle to sync (bottom right progress bar)

### 3. Configure JDK

1. Go to `File > Project Structure`
2. Under `Project > SDK`, select Java 11
   - If not available, click `Download JDK...`
   - Choose version 11, vendor "Eclipse Temurin"

### 4. Run the Plugin

**Option A: Using IntelliJ**

1. Open `src/test/java/com/osrsml/OSRSMLPluginTest.java`
2. Click the green play button next to `main()`
3. RuneLite will launch with your plugin loaded

**Option B: Using Gradle**

```bash
# Build the project
./gradlew build

# Run with shadowJar (creates runnable JAR)
./gradlew shadowJar
java -jar build/libs/osrs-ml-ai-player-1.0.0-all.jar
```

### 5. Verify Plugin is Loaded

1. In RuneLite, click the wrench icon (Configuration)
2. Search for "OSRS ML AI Player"
3. Enable the plugin
4. You should see the overlay in the top-left corner

## Troubleshooting

### Plugin Not Showing in List

1. **Check `runelite-plugin.properties`** - Must be in project root
2. **Verify package name matches** - `plugins=com.osrsml.OSRSMLPlugin`
3. **Clean and rebuild**:
   ```bash
   ./gradlew clean build
   ```

### Gradle Sync Failed

1. **Check internet connection** - Needs to download dependencies
2. **Invalidate caches**: `File > Invalidate Caches > Invalidate and Restart`
3. **Check Java version** - Must be Java 11

### Overlay Not Visible

1. Make sure you're logged into the game
2. Check plugin settings - "Show Overlay" should be enabled
3. Look in top-left corner of game window

## Development

### Adding New Features

1. Add event subscribers in `OSRSMLPlugin.java`:
   ```java
   @Subscribe
   public void onChatMessage(ChatMessage event) {
       // Handle chat messages
   }
   ```

2. Add config options in `OSRSMLConfig.java`:
   ```java
   @ConfigItem(keyName = "myOption", name = "My Option", description = "Description")
   default boolean myOption() { return true; }
   ```

3. Update overlay in `OSRSMLOverlay.java`

### Useful RuneLite APIs

- `Client` - Game state, player info, world data
- `ConfigManager` - Save/load settings
- `OverlayManager` - Manage overlays
- `ChatMessageManager` - Send chat messages
- `ItemManager` - Item data and images

## Resources

- [RuneLite Wiki](https://github.com/runelite/runelite/wiki)
- [RuneLite API Docs](https://static.runelite.net/api/runelite-api/)
- [Plugin Hub Guide](https://github.com/runelite/plugin-hub)

## License

This project is for educational purposes only.
