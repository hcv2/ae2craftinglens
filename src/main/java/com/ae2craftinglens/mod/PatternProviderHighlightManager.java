package com.ae2craftinglens.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class PatternProviderHighlightManager {
    private static final PatternProviderHighlightManager INSTANCE = new PatternProviderHighlightManager();
    
    private final Map<UUID, List<HighlightedProvider>> playerHighlights = new HashMap<>();
    private static final long HIGHLIGHT_DURATION_MS = 12000;
    
    private PatternProviderHighlightManager() {}
    
    public static PatternProviderHighlightManager getInstance() {
        return INSTANCE;
    }
    
    public void addHighlightedProvider(UUID playerId, Level level, BlockPos pos) {
        if (playerId == null || level == null || pos == null) return;
        
        List<HighlightedProvider> playerList = playerHighlights.computeIfAbsent(playerId, k -> new ArrayList<>());
        playerList.removeIf(hp -> hp.matches(level, pos));
        playerList.add(new HighlightedProvider(playerId, level, pos, System.currentTimeMillis() + HIGHLIGHT_DURATION_MS));
    }
    
    public void addHighlightedProviders(UUID playerId, Level level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            addHighlightedProvider(playerId, level, pos);
        }
    }
    
    public List<HighlightedProvider> getActiveHighlights(UUID playerId) {
        if (playerId == null) return Collections.emptyList();
        
        List<HighlightedProvider> playerList = playerHighlights.get(playerId);
        if (playerList == null || playerList.isEmpty()) {
            return Collections.emptyList();
        }
        
        long currentTime = System.currentTimeMillis();
        playerList.removeIf(hp -> hp.isExpired(currentTime));
        return new ArrayList<>(playerList);
    }
    
    public List<HighlightedProvider> getAllActiveHighlights() {
        List<HighlightedProvider> allHighlights = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (List<HighlightedProvider> playerList : playerHighlights.values()) {
            playerList.removeIf(hp -> hp.isExpired(currentTime));
            allHighlights.addAll(playerList);
        }
        
        return allHighlights;
    }
    
    public void clearHighlights(UUID playerId) {
        if (playerId != null) {
            playerHighlights.remove(playerId);
        }
    }
    
    public void clearAllHighlights() {
        playerHighlights.clear();
    }
    
    public boolean hasActiveHighlights(UUID playerId) {
        if (playerId == null) return false;
        
        List<HighlightedProvider> playerList = playerHighlights.get(playerId);
        if (playerList == null || playerList.isEmpty()) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        playerList.removeIf(hp -> hp.isExpired(currentTime));
        return !playerList.isEmpty();
    }
    
    public boolean hasAnyActiveHighlights() {
        boolean hasAny = false;
        long currentTime = System.currentTimeMillis();
        
        for (List<HighlightedProvider> playerList : playerHighlights.values()) {
            playerList.removeIf(hp -> hp.isExpired(currentTime));
            if (!playerList.isEmpty()) {
                hasAny = true;
            }
        }
        
        return hasAny;
    }
    
    public static class HighlightedProvider {
        private final UUID playerId;
        private final Level level;
        private final BlockPos pos;
        private final long expireTime;
        
        public HighlightedProvider(UUID playerId, Level level, BlockPos pos, long expireTime) {
            this.playerId = playerId;
            this.level = level;
            this.pos = pos;
            this.expireTime = expireTime;
        }
        
        public UUID getPlayerId() {
            return playerId;
        }
        
        public Level getLevel() {
            return level;
        }
        
        public BlockPos getPos() {
            return pos;
        }
        
        public boolean isExpired(long currentTime) {
            return currentTime >= expireTime;
        }
        
        public boolean matches(Level level, BlockPos pos) {
            return this.level == level && this.pos.equals(pos);
        }
        
        public float getRemainingSeconds() {
            return Math.max(0, (expireTime - System.currentTimeMillis()) / 1000.0f);
        }
    }
}
