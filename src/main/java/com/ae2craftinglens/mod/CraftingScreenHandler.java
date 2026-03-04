package com.ae2craftinglens.mod;

import appeng.api.stacks.AEKey;

import com.ae2craftinglens.mod.network.NetworkHandler;
import com.ae2craftinglens.mod.network.RequestPatternProvidersPacket;

import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CraftingScreenHandler {
    private AEKey lastClickedAEKey = null;

    public CraftingScreenHandler() {
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("CraftingScreenHandler instance created");
        }
    }
    
    @SubscribeEvent
    public void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event received ===");
        }
        
        Object screen = event.getScreen();
        if (screen == null) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Screen is null, skipping");
            }
            return;
        }
        
        String screenClassName = screen.getClass().getName();
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Screen class: {}", screenClassName);
        }
        
        boolean isCraftingStatusScreen = screenClassName.contains("CraftingStatusScreen");
        boolean isWCTScreen = screenClassName.contains("WCTScreen") && screenClassName.contains("ae2wtlib");
        boolean isTerminalScreen = screenClassName.contains("TerminalScreen");
        
        if (!isCraftingStatusScreen && !isWCTScreen && !isTerminalScreen) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Not a CraftingStatusScreen or WCTScreen, skipping");
            }
            return;
        }
        
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Screen type: {} (CraftingStatusScreen: {}, WCTScreen: {})", 
                    isCraftingStatusScreen ? "CraftingStatusScreen" : "WCTScreen", 
                    isCraftingStatusScreen, isWCTScreen);
        }
        
        if (event.getButton() != 0) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Not left mouse button, skipping");
            }
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Minecraft instance is null, skipping");
            }
            return;
        }
        if (mc.player == null) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Player is null, skipping");
            }
            return;
        }
        
        boolean isShiftPressed = net.minecraft.client.gui.screens.Screen.hasShiftDown() || mc.options.keyShift.isDown();
        
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Shift key pressed: {}", isShiftPressed);
        }
        
        if (!isShiftPressed) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Shift not pressed, skipping");
            }
            return;
        }
        
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Mouse position: {}, {}", event.getMouseX(), event.getMouseY());
        }
        
        if (isClickOnButton(event)) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Click is on a button, letting AE2 handle it");
            }
            return;
        }
        
        if (!isClickOnCraftingItem(screen, event.getMouseX(), event.getMouseY())) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Click is not on the crafting item, skipping");
            }
            return;
        }
        
        event.setCanceled(true);
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Event canceled to prevent opening targeted blocks");
        }
        
        try {
            int finalIndex = -1;
            int guiLeft = getGuiLeft(screen);
            int guiTop = getGuiTop(screen);
            double mouseX = event.getMouseX();
            double mouseY = event.getMouseY();
            double relX = mouseX - guiLeft;
            double relY = mouseY - guiTop;

            boolean isGridMode = isGridMode(screen);
            
            // Log raw field value for debugging
            Object rawGridMode = getFieldValueWithSrg(screen, "gridMode", "f_96540_");
            AE2CraftingLens.LOGGER.info("Screen: {}, isGridMode raw value: {} (type: {})", screenClassName, rawGridMode, rawGridMode != null ? rawGridMode.getClass().getName() : "null");

            int tableStartX, tableStartY, tableWidth, tableHeight;
            
            if (isCraftingStatusScreen) {
                tableStartX = 9;
                tableStartY = 19;
                tableWidth = 204;
                tableHeight = isGridMode ? 108 : 115;
            } else if (isWCTScreen) {
                tableStartX = 57;
                tableStartY = 9;
                tableWidth = 152;
                tableHeight = isGridMode ? 108 : 90;
            } else if (isTerminalScreen) {
                tableStartX = 170;
                tableStartY = 19;
                tableWidth = 204;
                tableHeight = isGridMode ? 108 : 115;
            } else {
                tableStartX = 70;
                tableStartY = 29;
                tableWidth = 130;
                tableHeight = isGridMode ? 108 : 90;
            }

            int rowHeight = isGridMode ? 18 : 23;
            int columns = getColumns(screen, isGridMode, tableWidth);
            
            int clickedRow = (int) Math.floor((relY - tableStartY) / rowHeight);
            int clickedCol = isGridMode ? (int) Math.floor((relX - tableStartX) / (tableWidth / (double) columns)) : 0;
            
            int scrollOffset = getScrollOffset(screen, isGridMode);
            finalIndex = (clickedRow + scrollOffset) * columns + clickedCol;

            AE2CraftingLens.LOGGER.info("Click Debug: mouse=({}, {}), gui=({}, {}), rel=({}, {}), isGrid={}, tableStart=({}, {}), rowH={}, cols={}, clickedRow={}, clickedCol={}, scroll={}, finalIndex={}", 
                    mouseX, mouseY, guiLeft, guiTop, relX, relY, isGridMode, tableStartX, tableStartY, rowHeight, columns, clickedRow, clickedCol, scrollOffset, finalIndex);

            AEKey aeKey = getHoveredAEKey(screen, finalIndex);

            // Diagnostic: Log nearby items to understand grid layout
            try {
                Object status = getFieldValueWithSrg(screen, "status", "f_96541_");
                if (status != null) {
                    Object entriesObj = getFieldValueWithSrg(status, "entries", "f_38840_");
                    if (entriesObj instanceof List) {
                        List<?> entries = (List<?>) entriesObj;
                        AE2CraftingLens.LOGGER.info("Diagnostic: Total entries: {}", entries.size());
                        for (int i = 0; i < Math.min(entries.size(), 22); i++) {
                            AEKey key = extractAEKeyFromEntry(entries.get(i));
                            AE2CraftingLens.LOGGER.info("Diagnostic: Index {}: {}", i, key);
                        }
                    }
                }
            } catch (Exception e) {}

            if (aeKey == null) {
                AE2CraftingLens.LOGGER.info("No AEKey from hover detection, skipping");
                return;
            }

            AE2CraftingLens.LOGGER.info("Sending AEKey to server: {} (finalIndex: {}, row: {}, col: {}, isGrid: {})", 
                    aeKey, finalIndex, clickedRow + scrollOffset, clickedCol, isGridMode);

            RequestPatternProvidersPacket packet = new RequestPatternProvidersPacket(aeKey, finalIndex);
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Sending RequestPatternProvidersPacket with AEKey: {} and finalIndex: {}", aeKey, finalIndex);
            }
            NetworkHandler.CHANNEL.sendToServer(packet);
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Packet sent successfully to server");
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling mouse click", e);
        }
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event processed ===");
        }
    }
    
    private AEKey getHoveredAEKey(Object screen, int finalIndex) {
        try {
            // 1. Primary path: try to extract from 'table' field
            AEKey keyFromTable = extractAEKeyFromTable(screen, finalIndex);
            if (keyFromTable != null) {
                this.lastClickedAEKey = keyFromTable;
                return keyFromTable;
            }

            // 2. Secondary path: try to extract from 'status.entries'
            Object status = getFieldValueWithSrg(screen, "status", "f_96541_");
            if (status == null) {
                status = findFieldByType(screen, "appeng.me.cluster.implementations.CraftingJobStatus");
            }
            if (status != null) {
                Object entriesObj = getFieldValueWithSrg(status, "entries", "f_38840_");
                if (entriesObj == null) {
                    entriesObj = findFieldByType(status, "java.util.List");
                }
                if (entriesObj instanceof List) {
                    List<?> entries = (List<?>) entriesObj;
                    if (finalIndex >= 0 && finalIndex < entries.size()) {
                        Object entry = entries.get(finalIndex);
                        AEKey key = extractAEKeyFromEntry(entry);
                        if (key != null) {
                            this.lastClickedAEKey = key;
                            return key;
                        }
                    }
                }
            }

            // 3. Final fallback: ONLY return lastClickedAEKey if we have one
            return this.lastClickedAEKey;
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("getHoveredAEKey error: {}", e.getMessage());
        }
        return null;
    }

    private int getColumns(Object screen, boolean isGridMode, int tableWidth) {
        if (!isGridMode) return 1;
        
        // Check for WCT side panel
        String className = screen.getClass().getName();
        if (className.contains("WCT") || className.contains("ae2wtlib")) {
            if (tableWidth < 100) return 3;
        }
        
        // Standard AE2 grid is 11 columns
        if (tableWidth >= 190) return 11;
        
        // Dynamic detection for other widths
        if (tableWidth < 60) return 3;
        if (tableWidth < 100) return 5;
        
        return 11;
    }

    private boolean isGridMode(Object screen) {
        // Try common names in hierarchy with type check
        String[] possibleNames = {"gridMode", "f_96540_", "isGridMode", "grid"};
        for (String name : possibleNames) {
            try {
                java.lang.reflect.Field field = findFieldInHierarchy(screen.getClass(), name);
                if (field != null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
                    field.setAccessible(true);
                    return (boolean) field.get(screen);
                }
            } catch (Exception e) {}
        }

        // Try searching all boolean fields in hierarchy
        try {
            Class<?> clazz = screen.getClass();
            while (clazz != null && !clazz.equals(Object.class)) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                        f.setAccessible(true);
                        String name = f.getName();
                        Object val = f.get(screen);
                        AE2CraftingLens.LOGGER.debug("Checking boolean field: {} = {}", name, val);
                        if (name.toLowerCase().contains("grid") && val instanceof Boolean) {
                            return (boolean) val;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {}

        // Fallback: Check table size if possible
        try {
            Object status = getFieldValueWithSrg(screen, "status", "f_96541_");
            if (status != null) {
                Object entriesObj = getFieldValueWithSrg(status, "entries", "f_38840_");
                if (entriesObj instanceof java.util.Collection) {
                    int size = ((java.util.Collection<?>) entriesObj).size();
                    if (size > 5) {
                        // In list mode, usually only 5 items are visible, but the list can be longer.
                        // However, if we see many items, it's more likely to be a grid if we can't find the flag.
                        // But this is risky. Let's look at the scrollbar.
                        Object scrollbar = getFieldValueWithSrg(screen, "scrollbar", "f_96537_");
                        if (scrollbar != null) {
                            // If there are many items but no scrollbar, it MUST be a grid.
                            // Or if the scrollbar is very small.
                        }
                    }
                }
            }
        } catch (Exception e) {}

        return false;
    }

    private AEKey extractAEKeyFromTable(Object screen, int index) {
        try {
            Object table = getFieldValueWithSrg(screen, "table", "f_96542_");
            if (table != null) {
                if (table.getClass().isArray()) {
                    Object[] tableArray = (Object[]) table;
                    if (index >= 0 && index < tableArray.length) {
                        Object entry = tableArray[index];
                        return extractAEKeyFromEntry(entry);
                    }
                } else if (table instanceof List) {
                    List<?> tableList = (List<?>) table;
                    if (index >= 0 && index < tableList.size()) {
                        Object entry = tableList.get(index);
                        return extractAEKeyFromEntry(entry);
                    }
                }
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error extracting from table: {}", e.getMessage());
        }
        return null;
    }

    private AEKey extractAEKeyFromEntry(Object entry) {
        if (entry == null) return null;
        try {
            Object whatObj = getFieldValueWithSrg(entry, "what", "f_38839_");
            if (whatObj instanceof AEKey) {
                return (AEKey) whatObj;
            }
            // Try field finding by type
            whatObj = findFieldByType(entry, "appeng.api.stacks.AEKey");
            if (whatObj instanceof AEKey) {
                return (AEKey) whatObj;
            }
            // Try method what()
            java.lang.reflect.Method whatMethod = entry.getClass().getMethod("what");
            Object result = whatMethod.invoke(entry);
            if (result instanceof AEKey) {
                return (AEKey) result;
            }
        } catch (Exception e) {}
        return null;
    }

    private Object findFieldByType(Object obj, String typeName) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (field.getType().getName().equals(typeName)) {
                    try {
                        field.setAccessible(true);
                        return field.get(obj);
                    } catch (Exception e) {}
                }
                // Also check if the type is a subclass of typeName
                try {
                    Class<?> targetClass = Class.forName(typeName);
                    if (targetClass.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return field.get(obj);
                    }
                } catch (Exception e) {}
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
    
    private int getGuiLeft(Object screen) {
        try {
            return (int) screen.getClass().getMethod("getGuiLeft").invoke(screen);
        } catch (Exception e) {
            Object value = getFieldValueWithSrg(screen, "leftPos", "f_97734_");
            if (value instanceof Integer) {
                return (Integer) value;
            }
        }
        return 0;
    }
    
    private int getGuiTop(Object screen) {
        try {
            return (int) screen.getClass().getMethod("getGuiTop").invoke(screen);
        } catch (Exception e) {
            Object value = getFieldValueWithSrg(screen, "topPos", "f_97735_");
            if (value instanceof Integer) {
                return (Integer) value;
            }
        }
        return 0;
    }
    
    private boolean isClickOnButton(ScreenEvent.MouseButtonPressed.Pre event) {
        try {
            Object screen = event.getScreen();
            double mouseX = event.getMouseX();
            double mouseY = event.getMouseY();
            
            String[] possibleFields = {"renderables", "children", "widgets", "buttons", "listeners"};
            
            for (String fieldName : possibleFields) {
                try {
                    java.lang.reflect.Field field = screen.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(screen);
                    if (fieldValue instanceof Iterable) {
                        AE2CraftingLens.LOGGER.debug("Checking components in field: {}", fieldName);
                        Iterable<?> components = (Iterable<?>) fieldValue;
                        if (components != null) {
                            for (Object component : components) {
                                if (component == null) continue;
                                
                                String className = component.getClass().getName();
                                boolean isButton = className.contains("Button") || 
                                                   className.contains("Widget") ||
                                                   className.contains("ImageButton") ||
                                                   className.contains("IconButton") ||
                                                   className.contains("TextButton") ||
                                                   className.contains("Pressable") ||
                                                   className.contains("Clickable");
                                
                                if (isButton) {
                                    try {
                                        java.lang.reflect.Method getXMethod = component.getClass().getMethod("getX");
                                        java.lang.reflect.Method getYMethod = component.getClass().getMethod("getY");
                                        java.lang.reflect.Method getWidthMethod = component.getClass().getMethod("getWidth");
                                        java.lang.reflect.Method getHeightMethod = component.getClass().getMethod("getHeight");
                                        
                                        int x = (int) getXMethod.invoke(component);
                                        int y = (int) getYMethod.invoke(component);
                                        int width = (int) getWidthMethod.invoke(component);
                                        int height = (int) getHeightMethod.invoke(component);
                                        
                                        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                                            AE2CraftingLens.LOGGER.info("Click detected on button at ({}, {}) size ({}, {})", x, y, width, height);
                                            return true;
                                        }
                                    } catch (Exception e) {
                                        AE2CraftingLens.LOGGER.debug("Error getting button position for {}: {}", className, e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Field {} not found: {}", fieldName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error checking button click: {}", e.getMessage());
        }
        return false;
    }
    
    private int getScrollOffset(Object screen, boolean isGridMode) {
        try {
            Object status = getFieldValueWithSrg(screen, "status", "f_96541_");
            Object scrollbar = getFieldValueWithSrg(screen, "scrollbar", "f_96537_");
            
            if (status != null && scrollbar != null) {
                Object entriesObj = getFieldValueWithSrg(status, "entries", "f_38840_");
                Object currentScrollObj = getFieldValueWithSrg(scrollbar, "currentScroll", "f_96539_");
                
                if (entriesObj instanceof List && currentScrollObj != null) {
                    List<?> entries = (List<?>) entriesObj;
                    float currentScroll = 0;
                    if (currentScrollObj instanceof Float) {
                        currentScroll = (Float) currentScrollObj;
                    } else if (currentScrollObj instanceof Double) {
                        currentScroll = ((Double) currentScrollObj).floatValue();
                    }
                    
                    int totalItems = entries.size();
                    int columns = isGridMode ? 11 : 1;
                    int totalRows = (int) Math.ceil(totalItems / (double) columns);
                    int visibleRows = isGridMode ? 6 : 5;
                    
                    if (totalRows <= visibleRows) {
                        return 0;
                    }
                    
                    int scrollOffset = Math.round((totalRows - visibleRows) * currentScroll);
                    AE2CraftingLens.LOGGER.info("getScrollOffset: totalItems={}, columns={}, totalRows={}, visibleRows={}, currentScroll={}, scrollOffset={}", 
                            totalItems, columns, totalRows, visibleRows, currentScroll, scrollOffset);
                    
                    return scrollOffset;
                }
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("getScrollOffset error: {}", e.getMessage());
        }
        return 0;
    }

    private boolean isClickOnCraftingItem(Object screen, double mouseX, double mouseY) {
        try {
            int guiLeft = getGuiLeft(screen);
            int guiTop = getGuiTop(screen);
            
            double relativeX = mouseX - guiLeft;
            double relativeY = mouseY - guiTop;
            
            String screenClassName = screen.getClass().getName();
            boolean isCraftingStatusScreen = screenClassName.contains("CraftingStatusScreen");
            boolean isWCTScreen = screenClassName.contains("WCTScreen") && screenClassName.contains("ae2wtlib");
            boolean isTerminalScreen = screenClassName.contains("TerminalScreen");
            
            int tableStartX, tableStartY, tableWidth, tableHeight;
            boolean isGridMode = isGridMode(screen);
            
            if (isCraftingStatusScreen) {
                tableStartX = 9;
                tableStartY = 19;
                tableWidth = 204;
                tableHeight = isGridMode ? 108 : 115;
            } else if (isWCTScreen) {
                tableStartX = 57;
                tableStartY = 9;
                tableWidth = 152;
                tableHeight = isGridMode ? 108 : 90;
            } else if (isTerminalScreen) {
                tableStartX = 170;
                tableStartY = 19;
                tableWidth = 204;
                tableHeight = isGridMode ? 108 : 115;
            } else {
                tableStartX = 70;
                tableStartY = 29;
                tableWidth = 130;
                tableHeight = isGridMode ? 108 : 90;
            }
            
            boolean isOnTable = relativeX >= tableStartX && relativeX < tableStartX + tableWidth && 
                               relativeY >= tableStartY && relativeY < tableStartY + tableHeight;
            
            AE2CraftingLens.LOGGER.info("isClickOnCraftingItem: relativeX={}, relativeY={}, tableStartX={}, tableStartY={}, tableWidth={}, tableHeight={}, result={}", 
                    relativeX, relativeY, tableStartX, tableStartY, tableWidth, tableHeight, isOnTable);
            
            return isOnTable;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error checking crafting item click: {}", e.getMessage());
            return false;
        }
    }

    private java.lang.reflect.Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        if (fieldName == null) return null;
        while (clazz != null && !clazz.equals(Object.class)) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
    
    private Object getFieldValueWithSrg(Object obj, String mappedName, String srgName) {
        if (obj == null) return null;
        
        java.lang.reflect.Field field = null;
        
        // Try mapped name first
        field = findFieldInHierarchy(obj.getClass(), mappedName);
        
        // Try SRG name if mapped name not found
        if (field == null && srgName != null) {
            field = findFieldInHierarchy(obj.getClass(), srgName);
        }
        
        if (field != null) {
            try {
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error getting field '{}' from {}: {}", mappedName, obj.getClass().getName(), e.getMessage());
            }
        }
        
        return null;
    }
}
