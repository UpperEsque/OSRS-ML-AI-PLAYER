# OSRS ML AI Player

Reinforcement Learning AI for Old School RuneScape account progression.

## Features

- **Pure NumPy RL Training** - No PyTorch/TensorFlow required
- **RuneLite Plugin** - Integrates with the game client
- **Staged Training** - Train in chunks with checkpoints
- **40-Step Quest Guide** - From Tutorial Island to near-maxed account

## Project Structure
```
OSRS-ML-AI-PLAYER/
├── src/                    # RuneLite plugin (Java)
│   └── main/java/com/osrsml/
│       ├── OSRSMLPlugin.java
│       ├── OSRSMLConfig.java
│       └── OSRSMLOverlay.java
├── python/                 # RL Training (Python)
│   ├── train_numpy.py      # Environment + DQN
│   ├── train_staged.py     # Staged training
│   ├── bot_server.py       # HTTP server
│   └── staged_models/      # Saved checkpoints
└── gradle/                 # Build files
```

## Quick Start

### 1. Train the Model
```bash
cd python
pip install numpy flask
python3 train_staged.py --all
```

### 2. Start Bot Server
```bash
python3 bot_server.py
```

### 3. Build & Run RuneLite Plugin
```bash
./gradlew build
# Or install to RuneLite plugin folder
```

### 4. Enable in RuneLite

1. Open RuneLite settings
2. Find "OSRS ML AI Player"
3. Enable the plugin
4. Configure server host/port if needed

## Training Results

| Stage | Steps | Quest | Total Level | Max Skill |
|-------|-------|-------|-------------|-----------|
| 1 | 10k | 11/40 | 229 | 72 |
| 2 | 20k | 36/40 | 645 | 99 ✓ |
| 4 | 40k | 39/40 | 727 | 99 |
| 8 | 80k | 39/40 | 785 | 99 |

## Configuration

Plugin settings in RuneLite:
- **Enable Bot**: Toggle AI on/off
- **Server Host**: Bot server address (default: 127.0.0.1)
- **Server Port**: Bot server port (default: 5050)
- **Tick Delay**: Game ticks between actions (default: 3)

## License

MIT License - See LICENSE file

## Credits

- Based on [Naton's OSRS RL PvP project](https://github.com/naton-codeworksquestionnet/OSRS-ML-AI-PLAYER)
- RuneLite plugin framework
