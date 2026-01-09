#!/usr/bin/env python3
"""
OSRS Staged Training - Train in chunks of 10,000 steps
Saves progress after each stage so you can resume
"""

import numpy as np
import os
import time
import random
from collections import deque
from train_numpy import SimpleOSRSEnv, Action, QUEST_GUIDE, NumpyNetwork

class StagedTrainer:
    def __init__(self, hidden_dim=64, lr=0.001):
        self.env = SimpleOSRSEnv(max_steps=100000)  # Long enough for full run
        self.lr = lr
        self.gamma = 0.99
        self.batch_size = 32
        self.epsilon = 0.05  # Low exploration since we want optimal play
        
        # Networks
        self.q_net = NumpyNetwork(self.env.obs_dim, hidden_dim, self.env.num_actions)
        self.target_net = NumpyNetwork(self.env.obs_dim, hidden_dim, self.env.num_actions)
        self.target_net.copy_from(self.q_net)
        
        # Replay buffer
        self.buffer = deque(maxlen=50000)
        
        # Stats
        self.total_steps = 0
        self.best_quest = 0
        self.best_level = 0
        
        # Create saves directory
        os.makedirs("staged_models", exist_ok=True)
    
    def get_action(self, obs):
        if random.random() < self.epsilon:
            return random.randint(0, self.env.num_actions - 1)
        q = self.q_net.forward(obs.reshape(1, -1))
        return np.argmax(q[0])
    
    def train_step(self):
        if len(self.buffer) < self.batch_size:
            return
        
        indices = np.random.choice(len(self.buffer), self.batch_size, replace=False)
        batch = [self.buffer[i] for i in indices]
        
        states = np.array([x[0] for x in batch])
        actions = np.array([x[1] for x in batch])
        rewards = np.array([x[2] for x in batch])
        next_states = np.array([x[3] for x in batch])
        dones = np.array([x[4] for x in batch])
        
        next_q = self.target_net.forward(next_states)
        max_next_q = np.max(next_q, axis=1)
        targets = rewards + self.gamma * max_next_q * (1 - dones)
        
        current_q = self.q_net.forward(states)
        target_q = current_q.copy()
        for i in range(self.batch_size):
            target_q[i, actions[i]] = targets[i]
        
        self.q_net.backward(states, target_q, self.lr)
    
    def train_stage(self, start_step, end_step, stage_num):
        """Train from start_step to end_step"""
        print(f"\n{'='*60}")
        print(f"  STAGE {stage_num}: Steps {start_step:,} -> {end_step:,}")
        print(f"{'='*60}")
        
        # Reset environment for this stage
        obs = self.env.reset()
        
        # Fast-forward to start_step using AUTO_PROGRESS
        if start_step > 0:
            print(f"  Fast-forwarding to step {start_step:,}...")
            for _ in range(start_step):
                obs, _, done, _ = self.env.step(Action.AUTO_PROGRESS)
                if done:
                    obs = self.env.reset()
        
        stage_steps = end_step - start_step
        start_time = time.time()
        
        for step in range(stage_steps):
            current_step = start_step + step
            
            # Get action
            action = self.get_action(obs)
            
            # Step environment
            next_obs, reward, done, info = self.env.step(action)
            
            # Store transition
            self.buffer.append((obs, action, reward, next_obs, float(done)))
            
            obs = next_obs
            self.total_steps += 1
            
            # Train every 4 steps
            if self.total_steps % 4 == 0:
                self.train_step()
            
            # Update target network every 200 steps
            if self.total_steps % 200 == 0:
                self.target_net.copy_from(self.q_net)
            
            # Track best
            quest = info.get("quest_step", 0)
            level = info.get("total_level", 0)
            if quest > self.best_quest:
                self.best_quest = quest
            if level > self.best_level:
                self.best_level = level
            
            # Log every 1000 steps within stage
            if (step + 1) % 1000 == 0 or step == stage_steps - 1:
                elapsed = time.time() - start_time
                sps = (step + 1) / elapsed if elapsed > 0 else 0
                print(f"  Step {current_step + 1:>6,} | Quest: {quest:>2}/40 | "
                      f"Level: {int(sum(self.env.skills)):>3} | "
                      f"Max Skill: {int(max(self.env.skills)):>2} | {sps:.0f}/s")
            
            if done:
                break
        
        # Save after stage
        self.save_stage(stage_num, end_step)
        
        # Print stage summary
        print(f"\n  Stage {stage_num} Complete!")
        print(f"  Quest: {self.env.quest_step}/40")
        print(f"  Total Level: {int(sum(self.env.skills))}")
        print(f"  Skills: atk={int(self.env.skills[0])}, str={int(self.env.skills[1])}, "
              f"def={int(self.env.skills[2])}, mining={int(self.env.skills[9])}, "
              f"fish={int(self.env.skills[11])}, wc={int(self.env.skills[14])}")
        
        return self.env.quest_step >= 40  # Return True if completed
    
    def save_stage(self, stage_num, steps):
        path = f"staged_models/stage_{stage_num}_step_{steps}.npz"
        self.q_net.save(path)
        print(f"  Saved: {path}")
    
    def load_stage(self, path):
        self.q_net.load(path)
        self.target_net.copy_from(self.q_net)
        print(f"Loaded: {path}")
    
    def run_all_stages(self):
        """Run all stages from 0 to 80,000"""
        stages = [
            (0, 10000, 1),
            (10000, 20000, 2),
            (20000, 30000, 3),
            (30000, 40000, 4),
            (40000, 50000, 5),
            (50000, 60000, 6),
            (60000, 70000, 7),
            (70000, 80000, 8),
        ]
        
        print("\n" + "="*60)
        print("  OSRS STAGED TRAINING")
        print("  Training in 10,000 step chunks")
        print("="*60)
        
        for start, end, num in stages:
            completed = self.train_stage(start, end, num)
            if completed:
                print(f"\n{'='*60}")
                print("  QUEST GUIDE COMPLETED!")
                print(f"{'='*60}")
                break
        
        print(f"\n{'='*60}")
        print("  FINAL RESULTS")
        print(f"{'='*60}")
        print(f"  Best Quest Step: {self.best_quest}/40")
        print(f"  Best Total Level: {self.best_level}")
        print(f"  Total Training Steps: {self.total_steps:,}")


def run_single_stage(stage_num):
    """Run a single stage"""
    stages = {
        1: (0, 10000),
        2: (10000, 20000),
        3: (20000, 30000),
        4: (30000, 40000),
        5: (40000, 50000),
        6: (50000, 60000),
        7: (60000, 70000),
        8: (70000, 80000),
    }
    
    if stage_num not in stages:
        print(f"Invalid stage. Choose 1-8")
        return
    
    start, end = stages[stage_num]
    trainer = StagedTrainer()
    
    # Load previous stage if not stage 1
    if stage_num > 1:
        prev_stage = stage_num - 1
        prev_end = stages[prev_stage][1]
        prev_path = f"staged_models/stage_{prev_stage}_step_{prev_end}.npz"
        if os.path.exists(prev_path):
            trainer.load_stage(prev_path)
    
    trainer.train_stage(start, end, stage_num)


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", type=int, default=None, 
                        help="Run single stage (1-8). If not specified, runs all stages.")
    parser.add_argument("--all", action="store_true",
                        help="Run all stages sequentially")
    args = parser.parse_args()
    
    if args.stage:
        run_single_stage(args.stage)
    else:
        trainer = StagedTrainer()
        trainer.run_all_stages()
