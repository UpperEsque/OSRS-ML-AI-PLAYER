# OSRS ML Training

Pure NumPy reinforcement learning for OSRS account progression.

## Quick Start

### 1. Train the Model
```bash
cd python
python3 train_staged.py --all
```

This trains in 8 stages (10k steps each) and saves checkpoints to `staged_models/`.

### 2. Run the Bot Server
```bash
python3 bot_server.py
```

Server runs on `http://127.0.0.1:5050`

### 3. Enable Plugin in RuneLite
- Build and run RuneLite with the OSRS ML plugin
- Enable the plugin in settings
- Bot will receive game state and return recommended actions

## Files

| File | Description |
|------|-------------|
| `train_numpy.py` | Environment + DQN implementation (pure NumPy) |
| `train_staged.py` | Staged training system with checkpoints |
| `bot_server.py` | Flask HTTP server for RuneLite communication |

## Training Results

After 80,000 steps:
- Quest: 39/40
- Total Level: 785
- Skills: atk=98, str=98, def=99, mining=98, fish=97, wc=97

## Action Space

| ID | Action |
|----|--------|
| 0 | IDLE |
| 1-5 | WALK_TO (combat/mining/fishing/woodcutting/bank) |
| 6-8 | TRAIN_COMBAT (attack/strength/defence) |
| 9-11 | TRAIN_GATHERING (mining/fishing/woodcutting) |
| 12-14 | TRAIN_OTHER (cooking/firemaking/prayer) |
| 15 | DO_QUEST |
| 16 | BANK |
| 17 | EAT |
| 18 | AUTO_PROGRESS |

## Requirements

- Python 3.8+
- NumPy
- Flask (for bot server)
```bash
pip install numpy flask
```
