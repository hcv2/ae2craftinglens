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
    private int lastClickedRowIndex = -1;
    
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
        lastClickedAEKey = null;
        
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
        
        if (!isCraftingStatusScreen && !isWCTScreen) {
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
            int rowIndex = lastClickedRowIndex;
            lastClickedRowIndex = -1;

            AEKey aeKey = extractAEKeyFromTable(rowIndex);

            if (aeKey == null) {
                aeKey = lastClickedAEKey;
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Using UI-extracted AEKey: {}", aeKey);
                }
            }

            if (aeKey == null) {
                AE2CraftingLens.LOGGER.info("No AEKey from table, sending null to let server use rowIndex {} to find pattern", rowIndex);
            } else {
                AE2CraftingLens.LOGGER.info("Sending AEKey to server: {}, rowIndex: {}", aeKey, rowIndex);
            }

            RequestPatternProvidersPacket packet = new RequestPatternProvidersPacket(aeKey, rowIndex);
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Sending RequestPatternProvidersPacket with AEKey: {}, rowIndex: {}", aeKey, rowIndex);
            }
            NetworkHandler.CHANNEL.sendToServer(packet);
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Packet sent successfully to server");
            }
            lastClickedAEKey = null;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling mouse click", e);
        }
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event processed ===");
        }
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
    
    private AEKey extractAEKeyFromSlot(Object slot) {
        try {
            try {
                java.lang.reflect.Method getStackMethod = slot.getClass().getMethod("getStack");
                Object stack = getStackMethod.invoke(slot);
                if (stack != null) {
                    java.lang.reflect.Method whatMethod = stack.getClass().getMethod("what");
                    Object aeKey = whatMethod.invoke(stack);
                    if (aeKey != null) {
                        AE2CraftingLens.LOGGER.info("Extracted AEKey from GenericStack: {}", aeKey);
                        return (AEKey) aeKey;
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error extracting AEKey via GenericStack: {}", e.getMessage());
            }
            
            try {
                java.lang.reflect.Method getAEKeyMethod = slot.getClass().getMethod("getAEKey");
                Object aeKey = getAEKeyMethod.invoke(slot);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via getAEKey method: {}", aeKey);
                    return (AEKey) aeKey;
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error extracting AEKey via getAEKey: {}", e.getMessage());
            }
            
            try {
                java.lang.reflect.Field whatField = slot.getClass().getDeclaredField("what");
                whatField.setAccessible(true);
                Object aeKey = whatField.get(slot);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via what field: {}", aeKey);
                    return (AEKey) aeKey;
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error extracting AEKey via what field: {}", e.getMessage());
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error in extractAEKeyFromSlot: {}", e.getMessage());
        }
        return null;
    }

    private AEKey extractAEKeyFromTable(int rowIndex) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: mc or player is null");
                return null;
            }

            Object menu = mc.player.containerMenu;
            if (menu == null) {
                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: menu is null");
                return null;
            }

            String menuClassName = menu.getClass().getName();
            AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: menu class: {}, rowIndex: {}", menuClassName, rowIndex);
            
            if (!menuClassName.contains("CraftingStatusMenu")) {
                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: menu is not CraftingStatusMenu");
                return null;
            }

            Object screen = mc.screen;
            if (screen == null) {
                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: screen is null");
                return null;
            }
            
            AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: screen class: {}", screen.getClass().getName());

            // AE2 1.20.1: Search for CraftingJobStatus type field directly in Screen class
            AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Searching for CraftingJobStatus type field in screen...");
            Object status = null;
            
            for (java.lang.reflect.Field f : screen.getClass().getDeclaredFields()) {
                String fieldTypeName = f.getType().getName();
                if (fieldTypeName.contains("CraftingJobStatus")) {
                    AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Found CraftingJobStatus field '{}' type: {}", f.getName(), fieldTypeName);
                    f.setAccessible(true);
                    try {
                        status = f.get(screen);
                        if (status != null) {
                            AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Got status object from field '{}'", f.getName());
                            break;
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error getting status from field '{}': {}", f.getName(), e.getMessage());
                    }
                }
            }
            
            // If not found in screen, try in menu
            if (status == null) {
                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: CraftingJobStatus not found in screen, trying menu...");
                try {
                    java.lang.reflect.Method getMenuMethod = screen.getClass().getMethod("getMenu");
                    Object menuObj = getMenuMethod.invoke(screen);
                    if (menuObj != null) {
                        for (java.lang.reflect.Field f : menuObj.getClass().getDeclaredFields()) {
                            String fieldTypeName = f.getType().getName();
                            if (fieldTypeName.contains("CraftingJobStatus")) {
                                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Found CraftingJobStatus in menu field '{}' type: {}", f.getName(), fieldTypeName);
                                f.setAccessible(true);
                                status = f.get(menuObj);
                                if (status != null) {
                                    AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Got status object from menu field '{}'", f.getName());
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error getting menu: {}", e.getMessage());
                }
            }
            
            // If status found, extract entries
            if (status != null) {
                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Status found, searching for entries list...");
                
                for (java.lang.reflect.Field f : status.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        Object fieldValue = f.get(status);
                        if (fieldValue instanceof List) {
                            List<?> entries = (List<?>) fieldValue;
                            AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Found List in status.{} with size: {}", f.getName(), entries.size());
                            
                            if (rowIndex >= 0 && rowIndex < entries.size() && entries.get(rowIndex) != null) {
                                Object entry = entries.get(rowIndex);
                                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Found entry at index {}: {}", rowIndex, entry.getClass().getName());
                                
                                // Try to get 'what' field from entry
                                Object whatObj = getFieldValueWithSrg(entry, "what", "f_38839_");
                                if (whatObj instanceof AEKey) {
                                    AE2CraftingLens.LOGGER.info("=== SUCCESS: Extracted AEKey from status.entries[{}].what: {} ===", rowIndex, whatObj);
                                    return (AEKey) whatObj;
                                }
                                
                                // Fallback: try other methods
                                AEKey key = extractAEKeyFromObject(entry, rowIndex);
                                if (key != null) {
                                    return key;
                                }
                            }
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error accessing field '{}': {}", f.getName(), e.getMessage());
                    }
                }
            } else {
                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: CraftingJobStatus not found, trying table field...");
            }

            java.lang.reflect.Field tableField = findFieldInHierarchy(screen.getClass(), "table");
            AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: tableField found: {}", tableField != null);
            
            if (tableField != null) {
                tableField.setAccessible(true);
                Object table = tableField.get(screen);

                AE2CraftingLens.LOGGER.info("extractAEKeyFromTable: Got 'table' field, type: {}", 
                    table != null ? table.getClass().getName() : "null");
                
                if (table != null) {
                    String tableClassName = table.getClass().getName();
                    AE2CraftingLens.LOGGER.info("Table class: {}", tableClassName);

                    if (table instanceof Object[]) {
                        Object[] array = (Object[]) table;
                        AE2CraftingLens.LOGGER.info("Table array length: {}", array.length);
                        if (rowIndex >= 0 && rowIndex < array.length && array[rowIndex] != null) {
                            AE2CraftingLens.LOGGER.info("Table item at row {}: {}", rowIndex, array[rowIndex].getClass().getName());
                            AEKey key = extractAEKeyFromObject(array[rowIndex], rowIndex);
                            if (key != null) return key;
                        }
                    } else if (table instanceof List) {
                        List<?> list = (List<?>) table;
                        AE2CraftingLens.LOGGER.info("Table list size: {}", list.size());
                        if (rowIndex >= 0 && rowIndex < list.size() && list.get(rowIndex) != null) {
                            AE2CraftingLens.LOGGER.info("Table item at row {}: {}", rowIndex, list.get(rowIndex).getClass().getName());
                            AEKey key = extractAEKeyFromObject(list.get(rowIndex), rowIndex);
                            if (key != null) return key;
                        }
                    } else {
                        AE2CraftingLens.LOGGER.info("Table is not array or list, trying to extract 'status' field from: {}", tableClassName);
                        
                        // AE2 1.20.1 structure: Renderer -> status (CraftingJobStatus) -> entries (List<CraftingStatusEntry>) -> what (AEKey)
                        status = getFieldValueWithSrg(table, "status", "f_96541_");
                        if (status != null) {
                            AE2CraftingLens.LOGGER.info("Found 'status' field in table, type: {}", status.getClass().getName());
                            
                            // Try to get 'entries' from status
                            Object entriesObj = getFieldValueWithSrg(status, "entries", "f_38840_");
                            if (entriesObj instanceof List) {
                                List<?> entries = (List<?>) entriesObj;
                                AE2CraftingLens.LOGGER.info("Found 'entries' list in status with size: {}", entries.size());
                                
                                if (rowIndex >= 0 && rowIndex < entries.size() && entries.get(rowIndex) != null) {
                                    Object entry = entries.get(rowIndex);
                                    AE2CraftingLens.LOGGER.info("Found entry at index {}: {}", rowIndex, entry.getClass().getName());
                                    
                                    // Try to get 'what' field from entry
                                    Object whatObj = getFieldValueWithSrg(entry, "what", "f_38839_");
                                    if (whatObj instanceof AEKey) {
                                        AE2CraftingLens.LOGGER.info("=== SUCCESS: Extracted AEKey from status.entries[{}].what: {} ===", rowIndex, whatObj);
                                        return (AEKey) whatObj;
                                    }
                                }
                            } else {
                                AE2CraftingLens.LOGGER.info("'entries' not found in status, scanning all fields...");
                                for (java.lang.reflect.Field f : status.getClass().getDeclaredFields()) {
                                    f.setAccessible(true);
                                    try {
                                        Object fieldValue = f.get(status);
                                        if (fieldValue instanceof List) {
                                            List<?> list = (List<?>) fieldValue;
                                            AE2CraftingLens.LOGGER.info("  Found List in status.{} with size: {}", f.getName(), list.size());
                                            if (rowIndex >= 0 && rowIndex < list.size() && list.get(rowIndex) != null) {
                                                Object entry = list.get(rowIndex);
                                                AE2CraftingLens.LOGGER.info("  Found entry at index {}: {}", rowIndex, entry.getClass().getName());
                                                AEKey key = extractAEKeyFromObject(entry, rowIndex);
                                                if (key != null) {
                                                    AE2CraftingLens.LOGGER.info("=== SUCCESS: Extracted AEKey from status.{}[{}]: {} ===", f.getName(), rowIndex, key);
                                                    return key;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        AE2CraftingLens.LOGGER.debug("  Error accessing field '{}': {}", f.getName(), e.getMessage());
                                    }
                                }
                            }
                        } else {
                            AE2CraftingLens.LOGGER.info("'status' field not found in table, printing ALL fields for debugging...");
                            
                            // Print ALL fields with their types
                            java.lang.reflect.Field[] allFields = table.getClass().getDeclaredFields();
                            AE2CraftingLens.LOGGER.info("  Total fields in {}: {}", table.getClass().getName(), allFields.length);
                            
                            for (java.lang.reflect.Field f : allFields) {
                                try {
                                    f.setAccessible(true);
                                    Object fieldValue = f.get(table);
                                    String fieldTypeName = fieldValue != null ? fieldValue.getClass().getName() : "null";
                                    String fieldName = f.getName();
                                    AE2CraftingLens.LOGGER.info("  Field '{}' : type = {}", fieldName, fieldTypeName);
                                    
                                    // Check if field type contains "Status" or "Entry"
                                    if (fieldTypeName.contains("Status") || fieldTypeName.contains("Entry") || fieldTypeName.contains("List")) {
                                        AE2CraftingLens.LOGGER.info("    ^^^ POTENTIAL DATA FIELD ^^^");
                                    }
                                } catch (Exception e) {
                                    AE2CraftingLens.LOGGER.info("  Field '{}' : error = {}", f.getName(), e.getMessage());
                                }
                            }
                        }
                        
                        // Fallback: scan all fields
                        for (java.lang.reflect.Field f : table.getClass().getDeclaredFields()) {
                            try {
                                f.setAccessible(true);
                                Object fieldValue = f.get(table);
                                String fieldTypeName = fieldValue != null ? fieldValue.getClass().getName() : "null";
                                AE2CraftingLens.LOGGER.info("  Table field '{}' type: {}", f.getName(), fieldTypeName);
                                
                                if (fieldValue != null) {
                                    if (fieldValue instanceof List) {
                                        List<?> list = (List<?>) fieldValue;
                                        AE2CraftingLens.LOGGER.info("  Found List in table.{} with size: {}", f.getName(), list.size());
                                        if (rowIndex >= 0 && rowIndex < list.size() && list.get(rowIndex) != null) {
                                            AE2CraftingLens.LOGGER.info("  Found item at index {}: {}", rowIndex, list.get(rowIndex).getClass().getName());
                                            AEKey key = extractAEKeyFromObject(list.get(rowIndex), rowIndex);
                                            if (key != null) {
                                                AE2CraftingLens.LOGGER.info("=== SUCCESS: Extracted AEKey from table.{}[{}]: {} ===", f.getName(), rowIndex, key);
                                                return key;
                                            }
                                        }
                                    } else if (fieldValue instanceof Object[]) {
                                        Object[] array = (Object[]) fieldValue;
                                        AE2CraftingLens.LOGGER.info("  Found array in table.{} with length: {}", f.getName(), array.length);
                                        if (rowIndex >= 0 && rowIndex < array.length && array[rowIndex] != null) {
                                            AE2CraftingLens.LOGGER.info("  Found item at index {}: {}", rowIndex, array[rowIndex].getClass().getName());
                                            AEKey key = extractAEKeyFromObject(array[rowIndex], rowIndex);
                                            if (key != null) {
                                                AE2CraftingLens.LOGGER.info("=== SUCCESS: Extracted AEKey from table.{}[{}]: {} ===", f.getName(), rowIndex, key);
                                                return key;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                AE2CraftingLens.LOGGER.debug("  Error accessing field '{}': {}", f.getName(), e.getMessage());
                            }
                        }
                    }
                }
            } else {
                AE2CraftingLens.LOGGER.info("Could not find 'table' field in screen class hierarchy");
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.info("Error extracting AEKey from table: {}", e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private AEKey extractAEKeyFromObject(Object obj, int rowIndex) {
        if (obj == null) return null;

        try {
            String className = obj.getClass().getName();
            AE2CraftingLens.LOGGER.info("Extracting AEKey from object: {}", className);

            // Special handling for CraftingStatusEntry - direct 'what' field access
            if (className.contains("CraftingStatusEntry")) {
                AE2CraftingLens.LOGGER.info("Object is CraftingStatusEntry, trying 'what' field directly");
                try {
                    java.lang.reflect.Field whatField = obj.getClass().getDeclaredField("what");
                    whatField.setAccessible(true);
                    Object aeKey = whatField.get(obj);
                    if (aeKey != null) {
                        AE2CraftingLens.LOGGER.info("=== EXTRACTED AEKey from CraftingStatusEntry.what at row {}: {} ===", rowIndex, aeKey);
                        return (AEKey) aeKey;
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.info("Failed to get 'what' field from CraftingStatusEntry: {}", e.getMessage());
                }
            }

            try {
                java.lang.reflect.Method whatMethod = obj.getClass().getMethod("what");
                Object aeKey = whatMethod.invoke(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("=== EXTRACTED AEKey via what() at row {}: {} ===", rowIndex, aeKey);
                    return (AEKey) aeKey;
                }
            } catch (Exception e) {
            }

            try {
                java.lang.reflect.Method getKeyMethod = obj.getClass().getMethod("getKey");
                Object aeKey = getKeyMethod.invoke(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("=== EXTRACTED AEKey via getKey() at row {}: {} ===", rowIndex, aeKey);
                    return (AEKey) aeKey;
                }
            } catch (Exception e) {
            }

            try {
                java.lang.reflect.Field whatField = obj.getClass().getDeclaredField("what");
                whatField.setAccessible(true);
                Object aeKey = whatField.get(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via what field at row {}: {}", rowIndex, aeKey);
                    return (AEKey) aeKey;
                }
            } catch (Exception e) {
            }

            try {
                java.lang.reflect.Field keyField = obj.getClass().getDeclaredField("key");
                keyField.setAccessible(true);
                Object aeKey = keyField.get(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via key field at row {}: {}", rowIndex, aeKey);
                    return (AEKey) aeKey;
                }
            } catch (Exception e) {
            }

            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(obj);
                    if (val != null) {
                        String typeName = val.getClass().getName();
                        if (typeName.contains("AEKey") || typeName.contains("AEItemKey") ||
                            typeName.contains("GenericStack")) {
                            AE2CraftingLens.LOGGER.info("Found potential AEKey field '{}' in object", f.getName());

                            if (typeName.contains("GenericStack")) {
                                java.lang.reflect.Method whatMethod = val.getClass().getMethod("what");
                                Object aeKey = whatMethod.invoke(val);
                                if (aeKey != null) {
                                    AE2CraftingLens.LOGGER.info("Extracted AEKey from nested GenericStack at row {}: {}", rowIndex, aeKey);
                                    return (AEKey) aeKey;
                                }
                            } else {
                                return (AEKey) val;
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }

        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error extracting AEKey from object: {}", e.getMessage());
        }

        return null;
    }

    private boolean isClickOnCraftingItem(Object screen, double mouseX, double mouseY) {
        try {
            java.lang.reflect.Method getGuiLeftMethod = screen.getClass().getMethod("getGuiLeft");
            java.lang.reflect.Method getGuiTopMethod = screen.getClass().getMethod("getGuiTop");
            int guiLeft = (int) getGuiLeftMethod.invoke(screen);
            int guiTop = (int) getGuiTopMethod.invoke(screen);
            
            double relativeX = mouseX - guiLeft;
            double relativeY = mouseY - guiTop;
            
            String screenClassName = screen.getClass().getName();
            boolean isCraftingStatusScreen = screenClassName.contains("CraftingStatusScreen");
            boolean isWCTScreen = screenClassName.contains("WCTScreen") && screenClassName.contains("ae2wtlib");
            
            if (isCraftingStatusScreen || isWCTScreen) {
                try {
                    java.lang.reflect.Field renderablesField = screen.getClass().getDeclaredField("renderables");
                    renderablesField.setAccessible(true);
                    Iterable<?> renderables = (Iterable<?>) renderablesField.get(screen);
                    
                    if (renderables != null) {
                        for (Object renderable : renderables) {
                            if (renderable == null) continue;
                            
                            String className = renderable.getClass().getName();
                            
                            if (className.contains("Slot") || className.contains("Item") || 
                                className.contains("Crafting") || className.contains("Job") || 
                                className.contains("Task") || className.contains("Stack") ||
                                className.contains("Renderable") && className.contains("Crafting")) {
                                try {
                                    java.lang.reflect.Method getXMethod = renderable.getClass().getMethod("getX");
                                    java.lang.reflect.Method getYMethod = renderable.getClass().getMethod("getY");
                                    java.lang.reflect.Method getWidthMethod = renderable.getClass().getMethod("getWidth");
                                    java.lang.reflect.Method getHeightMethod = renderable.getClass().getMethod("getHeight");
                                    int x = (int) getXMethod.invoke(renderable);
                                    int y = (int) getYMethod.invoke(renderable);
                                    int width = (int) getWidthMethod.invoke(renderable);
                                    int height = (int) getHeightMethod.invoke(renderable);
                                    
                                    if (relativeX >= x && relativeX < x + width && relativeY >= y && relativeY < y + height) {
                                        try {
                                            java.lang.reflect.Method getItemMethod = renderable.getClass().getMethod("getItem");
                                            Object item = getItemMethod.invoke(renderable);
                                            if (item != null) {
                                                AE2CraftingLens.LOGGER.info("Click detected on crafting item slot with item at ({}, {}) size ({}, {})", 
                                                        x, y, width, height);
                                                lastClickedAEKey = extractAEKeyFromSlot(renderable);
                                                return true;
                                            } else {
                                                AE2CraftingLens.LOGGER.info("Click on empty slot, skipping");
                                                return false;
                                            }
                                        } catch (Exception e) {
                                            AE2CraftingLens.LOGGER.info("Click detected on potential crafting item slot at ({}, {}) size ({}, {})", 
                                                    x, y, width, height);
                                            lastClickedAEKey = extractAEKeyFromSlot(renderable);
                                            return true;
                                        }
                                    }
                                } catch (Exception e) {
                                    AE2CraftingLens.LOGGER.debug("Slot without position info: {}", className);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error inspecting renderables: {}", e.getMessage());
                }
            }
            
            int itemX, itemY, itemWidth, itemHeight;
            
            if (isCraftingStatusScreen) {
                itemX = 9;
                itemY = 19;
                itemWidth = 204;
                itemHeight = 22;
                AE2CraftingLens.LOGGER.info("Using precise CraftingStatusScreen detection area based on AE2 source code (table area)");
            } else if (isWCTScreen) {
                itemX = 57;
                itemY = 9;
                itemWidth = 152;
                itemHeight = 16;
                AE2CraftingLens.LOGGER.info("Using adjusted WCTScreen detection area based on click coordinates");
            } else {
                itemX = 70;
                itemY = 29;
                itemWidth = 130;
                itemHeight = 22;
                AE2CraftingLens.LOGGER.info("Using default detection area for unknown screen type");
            }
            
            boolean isOnItem = false;
            int rowHeight = 23;
            int maxRows = 20;
            
            float deltaY = (float)(relativeY - itemY);
            int closestRow = Math.round(deltaY / rowHeight);
            
            if (closestRow < 0) closestRow = 0;
            if (closestRow >= maxRows) closestRow = maxRows - 1;
            
            int startRow = Math.max(0, closestRow - 2);
            int endRow = Math.min(maxRows - 1, closestRow + 2);
            
            AE2CraftingLens.LOGGER.debug("DeltaY: {}, closestRow: {}, checking rows {} to {}", deltaY, closestRow, startRow, endRow);
            
            for (int row = startRow; row <= endRow; row++) {
                int currentItemY = itemY + row * rowHeight;
                boolean isOnCurrentRow = relativeX >= itemX && relativeX < itemX + itemWidth && 
                                        relativeY >= currentItemY && relativeY < currentItemY + itemHeight;
                
                if (isOnCurrentRow) {
                    int cellXOffset = (int)(relativeX - itemX);
                    int cellCol = cellXOffset / 68;
                    if (cellCol >= 0 && cellCol < 3) {
                        isOnItem = true;
                        lastClickedRowIndex = row;
                        AE2CraftingLens.LOGGER.info("Click detected on crafting item row {}, col {} at y={} (deltaY={})", row, cellCol, currentItemY, deltaY);
                        break;
                    }
                }
            }
            
            if (!isOnItem) {
                AE2CraftingLens.LOGGER.debug("Click not detected on any crafting row. Details: relativeX={}, relativeY={}, itemX={}, itemY={}, rowHeight={}, deltaY={}, closestRow={}", 
                        relativeX, relativeY, itemX, itemY, rowHeight, deltaY, closestRow);
            }
            
            AE2CraftingLens.LOGGER.info("Checking crafting item area: GUI({}, {}), relative mouse: ({}, {}), table area: ({}, {}) size ({}, {}), cell size: 67x22, rows checked: {}-{}, result: {}",
                    guiLeft, guiTop, relativeX, relativeY, itemX, itemY, itemWidth, itemHeight, startRow, endRow, isOnItem);
            
            return isOnItem;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error checking crafting item click: {}", e.getMessage());
            return false;
        }
    }

    private java.lang.reflect.Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
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
