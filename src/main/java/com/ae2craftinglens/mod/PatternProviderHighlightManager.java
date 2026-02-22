package com.ae2craftinglens.mod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class PatternProviderHighlightManager {
    private static final PatternProviderHighlightManager INSTANCE = new PatternProviderHighlightManager();
    
    private final List<HighlightedProvider> highlightedProviders = new ArrayList<>();
    private static final long HIGHLIGHT_DURATION_MS = 12000;
    
    private PatternProviderHighlightManager() {}
    
    public static PatternProviderHighlightManager getInstance() {
        return INSTANCE;
    }
    
    public void addHighlightedProvider(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        
        highlightedProviders.removeIf(hp -> hp.matches(level, pos));
        highlightedProviders.add(new HighlightedProvider(level, pos, System.currentTimeMillis() + HIGHLIGHT_DURATION_MS));
    }
    
    public void addHighlightedProviders(Level level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            addHighlightedProvider(level, pos);
        }
    }
    
    public List<HighlightedProvider> getActiveHighlights() {
        long currentTime = System.currentTimeMillis();
        highlightedProviders.removeIf(hp -> hp.isExpired(currentTime));
        return new ArrayList<>(highlightedProviders);
    }
    
    public void clearHighlights() {
        highlightedProviders.clear();
    }
    
    public boolean hasActiveHighlights() {
        long currentTime = System.currentTimeMillis();
        highlightedProviders.removeIf(hp -> hp.isExpired(currentTime));
        return !highlightedProviders.isEmpty();
    }
    
    public static class HighlightedProvider {
        private final Level level;
        private final BlockPos pos;
        private final long expireTime;
        
        public HighlightedProvider(Level level, BlockPos pos, long expireTime) {
            this.level = level;
            this.pos = pos;
            this.expireTime = expireTime;
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
