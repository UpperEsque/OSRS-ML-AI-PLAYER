#!/usr/bin/env python3
"""
OSRS Progression RL - Pure NumPy Implementation
Uses tabular Q-learning with function approximation
No external dependencies beyond NumPy
"""

import numpy as np
import os
import time
import random
from collections import deque
from dataclasses import dataclass, field
from enum import IntEnum
from typing import Dict, List, Tuple


# =============================================================================
# SIMPLE ENVIRONMENT
# =============================================================================

class Action(IntEnum):
    """Simple 20-action space"""
    IDLE = 0
    WALK_TO_COMBAT = 1
    WALK_TO_MINING = 2
    WALK_TO_FISHING = 3
    WALK_TO_WOODCUTTING = 4
    WALK_TO_BANK = 5
    TRAIN_ATTACK = 6
    TRAIN_STRENGTH = 7
    TRAIN_DEFENCE = 8
    TRAIN_MINING = 9
    TRAIN_FISHING = 10
    TRAIN_WOODCUTTING = 11
    TRAIN_COOKING = 12
    TRAIN_FIREMAKING = 13
    TRAIN_PRAYER = 14
    DO_QUEST = 15
    BANK = 16
    EAT = 17
    AUTO_PROGRESS = 18


@dataclass
class QuestStep:
    name: str
    skill_reqs: Dict[str, int] = field(default_factory=dict)
    quest_point_req: int = 0
    rewards_xp: Dict[str, int] = field(default_factory=dict)
    rewards_qp: int = 0
    is_training: bool = False


# Simplified quest guide - balanced for achievable progression
QUEST_GUIDE = [
    # Phase 1: Tutorial & Early (0-5)
    QuestStep("Tutorial Island", {}, 0, {"attack": 100, "strength": 100, "defence": 100, "mining": 100, "fishing": 100, "woodcutting": 100}, 0),
    QuestStep("Cook's Assistant", {}, 0, {"cooking": 300}, 1),
    QuestStep("Sheep Shearer", {}, 0, {}, 1),
    QuestStep("The Restless Ghost", {}, 0, {"prayer": 1125}, 1),
    QuestStep("Rune Mysteries", {}, 0, {}, 1),
    QuestStep("Imp Catcher", {}, 0, {"magic": 875}, 1),
    
    # Phase 2: Early Combat (6-9)
    QuestStep("Train Combat 10", {"attack": 8, "strength": 8}, 0, {}, 0, True),
    QuestStep("Waterfall Quest", {}, 0, {"attack": 13750, "strength": 13750}, 1),
    QuestStep("Tree Gnome Village", {}, 0, {"attack": 11450}, 2),
    QuestStep("Train Combat 20", {"attack": 18, "strength": 18}, 0, {}, 0, True),
    
    # Phase 3: Early Skills (10-14)
    QuestStep("Doric's Quest", {}, 0, {"mining": 1300}, 1),
    QuestStep("Train Mining 15", {"mining": 12}, 0, {}, 0, True),
    QuestStep("The Knight's Sword", {"mining": 10}, 0, {"smithing": 12725}, 1),
    QuestStep("Train Fishing 15", {"fishing": 12}, 0, {}, 0, True),
    QuestStep("Train Woodcutting 15", {"woodcutting": 12}, 0, {}, 0, True),
    
    # Phase 4: Mid Combat (15-19)
    QuestStep("Witch's House", {}, 0, {"hitpoints": 6325}, 4),
    QuestStep("Vampire Slayer", {}, 0, {"attack": 4825}, 3),
    QuestStep("Train Combat 30", {"attack": 28, "strength": 28}, 0, {}, 0, True),
    QuestStep("Dragon Slayer I", {"quest_points": 12}, 0, {"strength": 18650, "defence": 18650}, 2),
    QuestStep("Train Defence 25", {"defence": 22}, 0, {}, 0, True),
    
    # Phase 5: Mid Skills (20-24)
    QuestStep("Train Mining 25", {"mining": 22}, 0, {}, 0, True),
    QuestStep("Train Fishing 25", {"fishing": 22}, 0, {}, 0, True),
    QuestStep("Train Woodcutting 25", {"woodcutting": 22}, 0, {}, 0, True),
    QuestStep("Train Combat 40", {"attack": 38, "strength": 38, "defence": 32}, 0, {}, 0, True),
    QuestStep("Heroes' Quest", {"quest_points": 18}, 0, {"attack": 3075, "defence": 3075}, 1),
    
    # Phase 6: High Combat (25-29)
    QuestStep("Train Combat 50", {"attack": 48, "strength": 48, "defence": 42}, 0, {}, 0, True),
    QuestStep("Monkey Madness I", {}, 0, {"attack": 35000, "strength": 35000, "defence": 35000}, 3),
    QuestStep("Train Skills 40", {"mining": 38, "fishing": 35, "woodcutting": 35}, 0, {}, 0, True),
    QuestStep("Train Combat 60", {"attack": 58, "strength": 58, "defence": 52}, 0, {}, 0, True),
    QuestStep("Recipe for Disaster", {"quest_points": 25}, 0, {"attack": 20000, "defence": 20000}, 10),
    
    # Phase 7: High Level (30-34)
    QuestStep("Train Combat 70", {"attack": 68, "strength": 68, "defence": 62}, 0, {}, 0, True),
    QuestStep("Train Skills 55", {"mining": 52, "fishing": 48, "woodcutting": 48}, 0, {}, 0, True),
    QuestStep("Dragon Slayer II", {"quest_points": 35}, 0, {"mining": 18000}, 5),
    QuestStep("Train Combat 80", {"attack": 78, "strength": 78, "defence": 72}, 0, {}, 0, True),
    QuestStep("Train Skills 70", {"mining": 65, "fishing": 60, "woodcutting": 60}, 0, {}, 0, True),
    
    # Phase 8: End Game (35-39)
    QuestStep("Train Combat 90", {"attack": 88, "strength": 88, "defence": 82}, 0, {}, 0, True),
    QuestStep("Train Skills 80", {"mining": 75, "fishing": 70, "woodcutting": 70}, 0, {}, 0, True),
    QuestStep("Near Max Combat", {"attack": 95, "strength": 95, "defence": 90}, 0, {}, 0, True),
    QuestStep("Near Max Skills", {"mining": 85, "fishing": 80, "woodcutting": 80}, 0, {}, 0, True),
    QuestStep("MAXED!", {"attack": 99, "strength": 99, "defence": 99, "mining": 99, "fishing": 99, "woodcutting": 99}, 0, {}, 0, True),
]


class SimpleOSRSEnv:
    """Simple OSRS environment"""
    
    SKILL_NAMES = ["attack", "strength", "defence", "ranged", "prayer", "magic", "hitpoints",
                   "runecrafting", "crafting", "mining", "smithing", "fishing", "cooking",
                   "firemaking", "woodcutting"]
    SKILL_TO_IDX = {name: i for i, name in enumerate(SKILL_NAMES)}
    
    def __init__(self, max_steps: int = 1000):
        self.max_steps = max_steps
        self.num_actions = len(Action)
        self.obs_dim = 30  # Simplified observation
        self.reset()
    
    def reset(self):
        # Skills (15 skills)
        self.skills = np.ones(15, dtype=np.float32)
        self.skills[6] = 10  # HP
        self.skill_xp = np.zeros(15, dtype=np.float32)
        self.skill_xp[6] = 1154
        
        # Location flags
        self.at_combat = False
        self.at_mining = False
        self.at_fishing = False
        self.at_woodcutting = False
        self.at_bank = False
        
        # Inventory
        self.food = 5
        self.ore = 0
        self.fish = 0
        self.logs = 0
        self.bones = 0
        
        # Status
        self.hp = 10
        self.max_hp = 10
        
        # Quest tracking
        self.quest_step = 1  # Start after tutorial
        self.quest_points = 0
        
        # Episode
        self.steps = 0
        self.total_level = int(np.sum(self.skills))
        
        # Complete tutorial
        self._apply_quest_rewards(QUEST_GUIDE[0])
        
        return self._get_obs()
    
    def _get_obs(self):
        obs = np.zeros(self.obs_dim, dtype=np.float32)
        
        # Skills normalized (15)
        obs[:15] = self.skills / 99.0
        
        # Location (5)
        obs[15] = 1.0 if self.at_combat else 0.0
        obs[16] = 1.0 if self.at_mining else 0.0
        obs[17] = 1.0 if self.at_fishing else 0.0
        obs[18] = 1.0 if self.at_woodcutting else 0.0
        obs[19] = 1.0 if self.at_bank else 0.0
        
        # Inventory (5)
        obs[20] = min(self.food, 28) / 28.0
        obs[21] = min(self.ore, 28) / 28.0
        obs[22] = min(self.fish, 28) / 28.0
        obs[23] = min(self.logs, 28) / 28.0
        obs[24] = min(self.bones, 28) / 28.0
        
        # Progress (5)
        obs[25] = self.quest_step / len(QUEST_GUIDE)
        obs[26] = self.quest_points / 100.0
        obs[27] = self.total_level / 500.0
        obs[28] = self.hp / max(1, self.max_hp)
        obs[29] = self.steps / self.max_steps
        
        return obs
    
    def _xp_to_level(self, xp):
        level = 1
        total = 0
        for lvl in range(1, 100):
            total += int(lvl + 300 * (2 ** (lvl / 7))) // 4
            if xp >= total:
                level = lvl + 1
            else:
                break
        return min(99, level)
    
    def _gain_xp(self, skill_name, base_xp):
        if skill_name not in self.SKILL_TO_IDX:
            return 0.0
        
        idx = self.SKILL_TO_IDX[skill_name]
        old_level = self.skills[idx]
        
        # Aggressive XP scaling for faster progression to 99
        level = self.skills[idx]
        level_multiplier = 1 + level * 0.08 + (level / 40) ** 2.5
        xp = base_xp * level_multiplier * 3.0  # 3x base boost
        self.skill_xp[idx] += xp
        self.skills[idx] = self._xp_to_level(self.skill_xp[idx])
        
        reward = xp * 0.01
        if self.skills[idx] > old_level:
            reward += 25.0 * (self.skills[idx] - old_level)
        
        return reward
    
    def _apply_quest_rewards(self, quest):
        for skill, xp in quest.rewards_xp.items():
            if skill in self.SKILL_TO_IDX:
                idx = self.SKILL_TO_IDX[skill]
                self.skill_xp[idx] += xp
                self.skills[idx] = self._xp_to_level(self.skill_xp[idx])
        self.quest_points += quest.rewards_qp
    
    def _can_complete_quest(self, quest):
        for skill, req in quest.skill_reqs.items():
            if skill in self.SKILL_TO_IDX:
                if self.skills[self.SKILL_TO_IDX[skill]] < req:
                    return False
        if self.quest_points < quest.quest_point_req:
            return False
        return True
    
    def _clear_location(self):
        self.at_combat = self.at_mining = self.at_fishing = self.at_woodcutting = self.at_bank = False
    
    def step(self, action):
        self.steps += 1
        reward = 0.0
        action = Action(action)
        
        # Movement
        if action == Action.WALK_TO_COMBAT:
            self._clear_location()
            self.at_combat = True
        elif action == Action.WALK_TO_MINING:
            self._clear_location()
            self.at_mining = True
        elif action == Action.WALK_TO_FISHING:
            self._clear_location()
            self.at_fishing = True
        elif action == Action.WALK_TO_WOODCUTTING:
            self._clear_location()
            self.at_woodcutting = True
        elif action == Action.WALK_TO_BANK:
            self._clear_location()
            self.at_bank = True
        
        # Training
        elif action == Action.TRAIN_ATTACK:
            if self.at_combat:
                reward += self._train_combat("attack")
            else:
                reward = -0.1
        elif action == Action.TRAIN_STRENGTH:
            if self.at_combat:
                reward += self._train_combat("strength")
            else:
                reward = -0.1
        elif action == Action.TRAIN_DEFENCE:
            if self.at_combat:
                reward += self._train_combat("defence")
            else:
                reward = -0.1
        elif action == Action.TRAIN_MINING:
            if self.at_mining:
                reward += self._train_mining()
            else:
                reward = -0.1
        elif action == Action.TRAIN_FISHING:
            if self.at_fishing:
                reward += self._train_fishing()
            else:
                reward = -0.1
        elif action == Action.TRAIN_WOODCUTTING:
            if self.at_woodcutting:
                reward += self._train_woodcutting()
            else:
                reward = -0.1
        elif action == Action.TRAIN_COOKING:
            if self.fish > 0:
                self.fish -= 1
                self.food += 1
                reward += self._gain_xp("cooking", 30)
            else:
                reward = -0.1
        elif action == Action.TRAIN_FIREMAKING:
            if self.logs > 0:
                self.logs -= 1
                reward += self._gain_xp("firemaking", 40)
            else:
                reward = -0.1
        elif action == Action.TRAIN_PRAYER:
            if self.bones > 0:
                self.bones -= 1
                reward += self._gain_xp("prayer", 4.5)
            else:
                reward = -0.1
        
        # Quest
        elif action == Action.DO_QUEST:
            reward += self._try_quest()
        
        # Utility
        elif action == Action.BANK:
            if self.at_bank:
                self.ore = self.fish = self.logs = 0
                if self.food < 10:
                    self.food = 20
                reward = 0.1
        elif action == Action.EAT:
            if self.food > 0 and self.hp < self.max_hp:
                self.food -= 1
                self.hp = min(self.max_hp, self.hp + 10)
                reward = 0.1
        
        # Auto progress - smart action that picks optimal training
        elif action == Action.AUTO_PROGRESS:
            reward += self._auto_progress() + 0.3  # Small bonus for using smart action
        
        elif action == Action.IDLE:
            reward = -0.5  # Stronger penalty for wasting time
        
        # Update
        self.max_hp = int(self.skills[6])
        old_level = self.total_level
        self.total_level = int(np.sum(self.skills))
        
        # Check quest completion
        old_step = self.quest_step
        self._check_quests()
        
        if self.quest_step > old_step:
            reward += 150.0 * (self.quest_step - old_step)
        
        # Done
        done = self.steps >= self.max_steps or self.quest_step >= len(QUEST_GUIDE)
        if self.quest_step >= len(QUEST_GUIDE):
            reward += 5000.0
        
        info = {
            "quest_step": self.quest_step,
            "total_level": self.total_level,
            "quest_points": self.quest_points,
            "current_quest": QUEST_GUIDE[min(self.quest_step, len(QUEST_GUIDE)-1)].name
        }
        
        return self._get_obs(), reward, done, info
    
    def _train_combat(self, focus):
        if random.random() < 0.7:
            xp = 4 + (self.skills[0] + self.skills[1] + self.skills[2]) / 3 * 0.5
            r = 0.0
            if focus == "attack":
                r += self._gain_xp("attack", xp * 1.5)
            elif focus == "strength":
                r += self._gain_xp("strength", xp * 1.5)
            else:
                r += self._gain_xp("defence", xp * 1.5)
            r += self._gain_xp("hitpoints", xp * 1.33)
            if random.random() < 0.5:
                self.bones = min(28, self.bones + 1)
            return r
        return 0.01
    
    def _train_mining(self):
        if random.random() < 0.3 + self.skills[9] * 0.01:
            r = self._gain_xp("mining", 17.5)
            self.ore = min(28, self.ore + 1)
            return r
        return 0.01
    
    def _train_fishing(self):
        if random.random() < 0.25 + self.skills[11] * 0.01:
            r = self._gain_xp("fishing", 10)
            self.fish = min(28, self.fish + 1)
            return r
        return 0.01
    
    def _train_woodcutting(self):
        if random.random() < 0.2 + self.skills[14] * 0.01:
            r = self._gain_xp("woodcutting", 25)
            self.logs = min(28, self.logs + 1)
            return r
        return 0.01
    
    def _try_quest(self):
        if self.quest_step >= len(QUEST_GUIDE):
            return 0.0
        quest = QUEST_GUIDE[self.quest_step]
        if self._can_complete_quest(quest):
            self._apply_quest_rewards(quest)
            self.quest_step += 1
            return 200.0
        return 0.0
    
    def _check_quests(self):
        while self.quest_step < len(QUEST_GUIDE):
            quest = QUEST_GUIDE[self.quest_step]
            if self._can_complete_quest(quest):
                self._apply_quest_rewards(quest)
                self.quest_step += 1
            else:
                break
    
    def _auto_progress(self):
        if self.quest_step >= len(QUEST_GUIDE):
            return 0.0
        
        quest = QUEST_GUIDE[self.quest_step]
        
        # Find ALL skills that need training
        needed_skills = []
        for skill, req in quest.skill_reqs.items():
            if skill in self.SKILL_TO_IDX:
                current = self.skills[self.SKILL_TO_IDX[skill]]
                if current < req:
                    # Calculate how far behind we are
                    deficit = req - current
                    needed_skills.append((skill, req, deficit))
        
        if not needed_skills:
            return self._try_quest()
        
        # Sort by deficit (train the one we're furthest behind on)
        needed_skills.sort(key=lambda x: x[2], reverse=True)
        needed_skill = needed_skills[0][0]
        
        # Train what's needed
        if needed_skill in ["attack", "strength", "defence", "hitpoints"]:
            if not self.at_combat:
                self._clear_location()
                self.at_combat = True
                return 0.0
            return self._train_combat(needed_skill if needed_skill != "hitpoints" else "attack")
        elif needed_skill == "mining":
            if not self.at_mining:
                self._clear_location()
                self.at_mining = True
                return 0.0
            return self._train_mining()
        elif needed_skill == "fishing":
            if not self.at_fishing:
                self._clear_location()
                self.at_fishing = True
                return 0.0
            return self._train_fishing()
        elif needed_skill == "woodcutting":
            if not self.at_woodcutting:
                self._clear_location()
                self.at_woodcutting = True
                return 0.0
            return self._train_woodcutting()
        elif needed_skill == "cooking":
            if self.fish == 0:
                if not self.at_fishing:
                    self._clear_location()
                    self.at_fishing = True
                    return 0.0
                return self._train_fishing()
            self.fish -= 1
            self.food += 1
            return self._gain_xp("cooking", 30)
        elif needed_skill == "smithing":
            # Need smithing - simplified: just grant some XP
            return self._gain_xp("smithing", 15)
        elif needed_skill == "magic":
            return self._gain_xp("magic", 10)
        
        return 0.0


# =============================================================================
# NEURAL NETWORK (Pure NumPy)
# =============================================================================

class NumpyNetwork:
    """Simple neural network using only NumPy"""
    
    def __init__(self, input_dim, hidden_dim, output_dim):
        # Xavier initialization
        self.W1 = np.random.randn(input_dim, hidden_dim) * np.sqrt(2.0 / input_dim)
        self.b1 = np.zeros(hidden_dim)
        self.W2 = np.random.randn(hidden_dim, hidden_dim) * np.sqrt(2.0 / hidden_dim)
        self.b2 = np.zeros(hidden_dim)
        self.W3 = np.random.randn(hidden_dim, output_dim) * np.sqrt(2.0 / hidden_dim)
        self.b3 = np.zeros(output_dim)
    
    def forward(self, x):
        # Layer 1
        self.z1 = x @ self.W1 + self.b1
        self.a1 = np.maximum(0, self.z1)  # ReLU
        # Layer 2
        self.z2 = self.a1 @ self.W2 + self.b2
        self.a2 = np.maximum(0, self.z2)  # ReLU
        # Output
        self.out = self.a2 @ self.W3 + self.b3
        return self.out
    
    def backward(self, x, target, lr=0.001):
        """Backpropagation with gradient descent"""
        batch_size = x.shape[0]
        
        # Forward pass (save activations)
        self.forward(x)
        
        # Output layer gradient
        d_out = (self.out - target) / batch_size
        
        # Layer 3 gradients
        d_W3 = self.a2.T @ d_out
        d_b3 = np.sum(d_out, axis=0)
        
        # Backprop through layer 2
        d_a2 = d_out @ self.W3.T
        d_z2 = d_a2 * (self.z2 > 0)  # ReLU derivative
        
        d_W2 = self.a1.T @ d_z2
        d_b2 = np.sum(d_z2, axis=0)
        
        # Backprop through layer 1
        d_a1 = d_z2 @ self.W2.T
        d_z1 = d_a1 * (self.z1 > 0)  # ReLU derivative
        
        d_W1 = x.T @ d_z1
        d_b1 = np.sum(d_z1, axis=0)
        
        # Gradient clipping
        max_norm = 10.0
        for grad in [d_W1, d_W2, d_W3]:
            norm = np.linalg.norm(grad)
            if norm > max_norm:
                grad *= max_norm / norm
        
        # Update weights
        self.W1 -= lr * d_W1
        self.b1 -= lr * d_b1
        self.W2 -= lr * d_W2
        self.b2 -= lr * d_b2
        self.W3 -= lr * d_W3
        self.b3 -= lr * d_b3
    
    def copy_from(self, other):
        """Copy weights from another network"""
        self.W1 = other.W1.copy()
        self.b1 = other.b1.copy()
        self.W2 = other.W2.copy()
        self.b2 = other.b2.copy()
        self.W3 = other.W3.copy()
        self.b3 = other.b3.copy()
    
    def save(self, path):
        np.savez(path, W1=self.W1, b1=self.b1, W2=self.W2, b2=self.b2, W3=self.W3, b3=self.b3)
    
    def load(self, path):
        data = np.load(path)
        self.W1 = data['W1']
        self.b1 = data['b1']
        self.W2 = data['W2']
        self.b2 = data['b2']
        self.W3 = data['W3']
        self.b3 = data['b3']


# =============================================================================
# DQN TRAINER
# =============================================================================

class DQNTrainer:
    def __init__(self, 
                 total_steps=100000,
                 episode_length=1000,
                 hidden_dim=64,
                 lr=0.001,
                 gamma=0.99,
                 buffer_size=20000,
                 batch_size=32,
                 epsilon_start=1.0,
                 epsilon_end=0.05,
                 epsilon_decay=0.999,  # Faster decay
                 target_update=200,
                 train_freq=4,
                 log_interval=500):
        
        self.total_steps = total_steps
        self.lr = lr
        self.gamma = gamma
        self.batch_size = batch_size
        self.epsilon = epsilon_start
        self.epsilon_end = epsilon_end
        self.epsilon_decay = epsilon_decay
        self.target_update = target_update
        self.train_freq = train_freq
        self.log_interval = log_interval
        
        # Environment
        self.env = SimpleOSRSEnv(max_steps=episode_length)
        
        # Networks
        self.q_net = NumpyNetwork(self.env.obs_dim, hidden_dim, self.env.num_actions)
        self.target_net = NumpyNetwork(self.env.obs_dim, hidden_dim, self.env.num_actions)
        self.target_net.copy_from(self.q_net)
        
        # Replay buffer
        self.buffer = deque(maxlen=buffer_size)
        
        # Stats
        self.steps = 0
        self.episodes = 0
        self.episode_rewards = []
        self.best_reward = float('-inf')
        self.best_quest = 0
        self.best_level = 0
    
    def get_action(self, obs):
        if random.random() < self.epsilon:
            return random.randint(0, self.env.num_actions - 1)
        q = self.q_net.forward(obs.reshape(1, -1))
        return np.argmax(q[0])
    
    def train_step(self):
        if len(self.buffer) < self.batch_size:
            return
        
        # Sample batch
        indices = np.random.choice(len(self.buffer), self.batch_size, replace=False)
        batch = [self.buffer[i] for i in indices]
        
        states = np.array([x[0] for x in batch])
        actions = np.array([x[1] for x in batch])
        rewards = np.array([x[2] for x in batch])
        next_states = np.array([x[3] for x in batch])
        dones = np.array([x[4] for x in batch])
        
        # Compute targets
        next_q = self.target_net.forward(next_states)
        max_next_q = np.max(next_q, axis=1)
        targets = rewards + self.gamma * max_next_q * (1 - dones)
        
        # Current Q values
        current_q = self.q_net.forward(states)
        
        # Create target Q values (only update taken actions)
        target_q = current_q.copy()
        for i in range(self.batch_size):
            target_q[i, actions[i]] = targets[i]
        
        # Backprop
        self.q_net.backward(states, target_q, self.lr)
    
    def train(self):
        print(f"\n{'='*70}")
        print("   OSRS PROGRESSION RL - NumPy DQN")
        print(f"{'='*70}")
        print(f"   Total Steps: {self.total_steps:,}")
        print(f"   Actions: {self.env.num_actions}")
        print(f"   Observations: {self.env.obs_dim}")
        print(f"{'='*70}\n")
        
        obs = self.env.reset()
        episode_reward = 0
        start_time = time.time()
        
        while self.steps < self.total_steps:
            # Get action
            action = self.get_action(obs)
            
            # Step
            next_obs, reward, done, info = self.env.step(action)
            
            # Store transition
            self.buffer.append((obs, action, reward, next_obs, float(done)))
            
            obs = next_obs
            episode_reward += reward
            self.steps += 1
            
            # Train
            if self.steps % self.train_freq == 0:
                self.train_step()
            
            # Update target
            if self.steps % self.target_update == 0:
                self.target_net.copy_from(self.q_net)
            
            # Decay epsilon
            self.epsilon = max(self.epsilon_end, self.epsilon * self.epsilon_decay)
            
            # Episode done
            if done:
                self.episodes += 1
                self.episode_rewards.append(episode_reward)
                
                quest = info.get("quest_step", 0)
                level = info.get("total_level", 0)
                
                if quest > self.best_quest:
                    self.best_quest = quest
                if level > self.best_level:
                    self.best_level = level
                if episode_reward > self.best_reward:
                    self.best_reward = episode_reward
                
                obs = self.env.reset()
                episode_reward = 0
            
            # Log
            if self.steps % self.log_interval == 0:
                elapsed = time.time() - start_time
                sps = self.steps / elapsed if elapsed > 0 else 0
                avg_r = np.mean(self.episode_rewards[-20:]) if self.episode_rewards else 0
                
                quest_name = info.get("current_quest", "N/A")[:20]
                
                print(f"Step {self.steps:>6,} | Ep: {self.episodes:>3} | "
                      f"R: {avg_r:>7.1f} | Best: {self.best_reward:>7.1f} | "
                      f"Lvl: {self.best_level:>3} | Quest: {self.best_quest:>2}/{len(QUEST_GUIDE)} | "
                      f"Eps: {self.epsilon:.3f} | {sps:.0f}/s | {quest_name}")
        
        print(f"\n{'='*70}")
        print(f"   TRAINING COMPLETE!")
        print(f"   Episodes: {self.episodes}")
        print(f"   Best Reward: {self.best_reward:.2f}")
        print(f"   Best Quest Step: {self.best_quest}/{len(QUEST_GUIDE)}")
        print(f"   Best Total Level: {self.best_level}")
        print(f"{'='*70}")
        
        # Save model
        self.q_net.save("best_model.npz")


# =============================================================================
# MAIN
# =============================================================================

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser()
    parser.add_argument("--steps", type=int, default=100000)
    parser.add_argument("--episode-length", type=int, default=1000)
    parser.add_argument("--hidden", type=int, default=64)
    parser.add_argument("--lr", type=float, default=0.001)
    parser.add_argument("--log-interval", type=int, default=500)
    args = parser.parse_args()
    
    trainer = DQNTrainer(
        total_steps=args.steps,
        episode_length=args.episode_length,
        hidden_dim=args.hidden,
        lr=args.lr,
        log_interval=args.log_interval
    )
    
    trainer.train()
