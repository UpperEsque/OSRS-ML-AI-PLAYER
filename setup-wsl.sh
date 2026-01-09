#!/bin/bash

# OSRS-ML-AI-PLAYER WSL Setup Script
# Run this from inside your cloned repo directory

set -e  # Exit on error

echo "=========================================="
echo "OSRS ML AI Player - WSL Setup"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in the right directory
if [ ! -d ".git" ]; then
    echo -e "${RED}Error: Not in a git repository. Please cd into OSRS-ML-AI-PLAYER first.${NC}"
    exit 1
fi

echo -e "${YELLOW}Step 1: Checking Java installation...${NC}"
if ! command -v java &> /dev/null; then
    echo "Java not found. Installing OpenJDK 11..."
    sudo apt update
    sudo apt install openjdk-11-jdk -y
fi
java -version
echo -e "${GREEN}✓ Java OK${NC}"

echo ""
echo -e "${YELLOW}Step 2: Creating project structure...${NC}"

# Create directories
mkdir -p src/main/java/com/osrsml
mkdir -p src/test/java/com/osrsml
mkdir -p gradle/wrapper

echo -e "${GREEN}✓ Directories created${NC}"

echo ""
echo -e "${YELLOW}Step 3: Creating build.gradle...${NC}"

cat > build.gradle << 'EOF'
plugins {
    id 'java'
}

repositories {
    mavenLocal()
    maven {
        url = 'https://repo.runelite.net'
        content {
            includeGroupByRegex("net\\.runelite.*")
        }
    }
    mavenCentral()
}

def runeLiteVersion = 'latest.release'

dependencies {
    compileOnly group: 'net.runelite', name:'client', version: runeLiteVersion

    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'

    testImplementation 'junit:junit:4.12'
    testImplementation group: 'net.runelite', name:'client', version: runeLiteVersion
    testImplementation group: 'net.runelite', name:'jshell', version: runeLiteVersion
}

group = 'com.osrsml'
version = '1.0.0'

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release.set(11)
}

tasks.register('shadowJar', Jar) {
    dependsOn configurations.testRuntimeClasspath
    archiveClassifier.set('all')
    from sourceSets.main.output
    from sourceSets.test.output
    from {
        configurations.testRuntimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes 'Main-Class': 'com.osrsml.OSRSMLPluginTest'
    }
}
EOF
echo -e "${GREEN}✓ build.gradle created${NC}"

echo ""
echo -e "${YELLOW}Step 4: Creating settings.gradle...${NC}"

cat > settings.gradle << 'EOF'
rootProject.name = 'osrs-ml-ai-player'
EOF
echo -e "${GREEN}✓ settings.gradle created${NC}"

echo ""
echo -e "${YELLOW}Step 5: Creating runelite-plugin.properties (CRITICAL!)...${NC}"

cat > runelite-plugin.properties << 'EOF'
displayName=OSRS ML AI Player
author=UpperEsque
description=Machine Learning AI Player for Old School RuneScape
tags=ai,ml,machine-learning,automation
plugins=com.osrsml.OSRSMLPlugin
EOF
echo -e "${GREEN}✓ runelite-plugin.properties created${NC}"

echo ""
echo -e "${YELLOW}Step 6: Creating gradle wrapper properties...${NC}"

cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
echo -e "${GREEN}✓ gradle-wrapper.properties created${NC}"

echo ""
echo -e "${YELLOW}Step 7: Creating OSRSMLPlugin.java...${NC}"

cat > src/main/java/com/osrsml/OSRSMLPlugin.java << 'EOF'
package com.osrsml;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "OSRS ML AI Player",
    description = "Machine Learning AI Player for Old School RuneScape",
    tags = {"ai", "ml", "machine-learning"}
)
public class OSRSMLPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OSRSMLConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private OSRSMLOverlay overlay;

    @Override
    protected void startUp() throws Exception
    {
        log.info("OSRS ML AI Player started!");
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("OSRS ML AI Player stopped!");
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
        {
            log.info("Player logged in - AI systems ready");
        }
    }

    @Provides
    OSRSMLConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OSRSMLConfig.class);
    }
}
EOF
echo -e "${GREEN}✓ OSRSMLPlugin.java created${NC}"

echo ""
echo -e "${YELLOW}Step 8: Creating OSRSMLConfig.java...${NC}"

cat > src/main/java/com/osrsml/OSRSMLConfig.java << 'EOF'
package com.osrsml;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("osrsml")
public interface OSRSMLConfig extends Config
{
    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay",
        description = "Display the AI status overlay"
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "aiEnabled",
        name = "AI Enabled",
        description = "Enable AI decision making"
    )
    default boolean aiEnabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "debugMode",
        name = "Debug Mode",
        description = "Show detailed debug information"
    )
    default boolean debugMode()
    {
        return false;
    }
}
EOF
echo -e "${GREEN}✓ OSRSMLConfig.java created${NC}"

echo ""
echo -e "${YELLOW}Step 9: Creating OSRSMLOverlay.java...${NC}"

cat > src/main/java/com/osrsml/OSRSMLOverlay.java << 'EOF'
package com.osrsml;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class OSRSMLOverlay extends Overlay
{
    private final Client client;
    private final OSRSMLConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public OSRSMLOverlay(Client client, OSRSMLConfig config)
    {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("OSRS ML AI")
            .color(Color.GREEN)
            .build());

        // Check if logged in
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right("Not logged in")
                .rightColor(Color.RED)
                .build());
            return panelComponent.render(graphics);
        }

        // AI Status
        panelComponent.getChildren().add(LineComponent.builder()
            .left("AI Status:")
            .right(config.aiEnabled() ? "ACTIVE" : "DISABLED")
            .rightColor(config.aiEnabled() ? Color.GREEN : Color.YELLOW)
            .build());

        // Combat stats
        int attack = client.getBoostedSkillLevel(Skill.ATTACK);
        int strength = client.getBoostedSkillLevel(Skill.STRENGTH);
        int defence = client.getBoostedSkillLevel(Skill.DEFENCE);
        int hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

        panelComponent.getChildren().add(LineComponent.builder()
            .left("HP:")
            .right(hitpoints + "/" + maxHp)
            .rightColor(getHealthColor(hitpoints, maxHp))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Combat:")
            .right(attack + "/" + strength + "/" + defence)
            .build());

        // Prayer
        int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Prayer:")
            .right(prayer + "/" + maxPrayer)
            .rightColor(getPrayerColor(prayer, maxPrayer))
            .build());

        if (config.debugMode())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Debug:")
                .right("ON")
                .rightColor(Color.CYAN)
                .build());
        }

        return panelComponent.render(graphics);
    }

    private Color getHealthColor(int current, int max)
    {
        double ratio = (double) current / max;
        if (ratio > 0.5) return Color.GREEN;
        if (ratio > 0.25) return Color.YELLOW;
        return Color.RED;
    }

    private Color getPrayerColor(int current, int max)
    {
        double ratio = (double) current / max;
        if (ratio > 0.5) return Color.CYAN;
        if (ratio > 0.25) return Color.YELLOW;
        return Color.RED;
    }
}
EOF
echo -e "${GREEN}✓ OSRSMLOverlay.java created${NC}"

echo ""
echo -e "${YELLOW}Step 10: Creating OSRSMLPluginTest.java...${NC}"

cat > src/test/java/com/osrsml/OSRSMLPluginTest.java << 'EOF'
package com.osrsml;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OSRSMLPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(OSRSMLPlugin.class);
        RuneLite.main(args);
    }
}
EOF
echo -e "${GREEN}✓ OSRSMLPluginTest.java created${NC}"

echo ""
echo -e "${YELLOW}Step 11: Creating .gitignore...${NC}"

cat > .gitignore << 'EOF'
# Gradle
.gradle/
build/
out/

# IDE
.idea/
*.iml
*.ipr
*.iws
.vscode/

# OS
.DS_Store
Thumbs.db

# Compiled
*.class
*.jar
!gradle-wrapper.jar

# Logs
*.log
EOF
echo -e "${GREEN}✓ .gitignore created${NC}"

echo ""
echo -e "${YELLOW}Step 12: Downloading Gradle wrapper...${NC}"

# Download gradle wrapper jar
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar 2>/dev/null || \
    wget -O gradle/wrapper/gradle-wrapper.jar \
        https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar 2>/dev/null || \
    echo -e "${YELLOW}Warning: Could not download gradle-wrapper.jar. You may need to use system gradle.${NC}"
fi

# Create gradlew script
cat > gradlew << 'GRADLEW'
#!/bin/sh
exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
GRADLEW
chmod +x gradlew

echo -e "${GREEN}✓ Gradle wrapper configured${NC}"

echo ""
echo -e "${YELLOW}Step 13: Installing Gradle (if needed)...${NC}"

if ! command -v gradle &> /dev/null; then
    echo "Installing Gradle via SDKMAN..."
    if ! command -v sdk &> /dev/null; then
        curl -s "https://get.sdkman.io" | bash
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    fi
    sdk install gradle 8.5 || sudo apt install gradle -y
fi
echo -e "${GREEN}✓ Gradle OK${NC}"

echo ""
echo -e "${YELLOW}Step 14: Building project...${NC}"

# Use system gradle if wrapper doesn't work
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    ./gradlew build --no-daemon 2>&1 || gradle build --no-daemon
else
    gradle build --no-daemon
fi

echo ""
echo "=========================================="
echo -e "${GREEN}Setup Complete!${NC}"
echo "=========================================="
echo ""
echo "Project structure:"
find . -name "*.java" -o -name "*.gradle" -o -name "*.properties" | grep -v ".gradle/" | sort
echo ""
echo "=========================================="
echo "Next Steps:"
echo "=========================================="
echo ""
echo "Option 1: Run from WSL (requires X server like VcXsrv on Windows)"
echo "  export DISPLAY=:0"
echo "  gradle shadowJar"
echo "  java -jar build/libs/osrs-ml-ai-player-1.0.0-all.jar"
echo ""
echo "Option 2: Open in IntelliJ IDEA on Windows"
echo "  1. Open IntelliJ IDEA"
echo "  2. File > Open"
echo "  3. Navigate to: \\\\wsl$\\Ubuntu\\home\\$USER\\OSRS-ML-AI-PLAYER"
echo "  4. Wait for Gradle sync"
echo "  5. Run OSRSMLPluginTest.java"
echo ""
echo "Option 3: Build JAR and copy to Windows"
echo "  gradle shadowJar"
echo "  cp build/libs/*.jar /mnt/c/Users/YOUR_USER/Desktop/"
echo ""
