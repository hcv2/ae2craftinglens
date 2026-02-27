package com.ae2craftinglens.mod;

import com.ae2craftinglens.mod.network.NetworkHandler;
import com.ae2craftinglens.mod.network.RequestPatternProvidersPacket;

import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ScreenEvent;

public class CraftingScreenHandler {

    private Object lastClickedAEKey = null;
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

            Object aeKey = extractAEKeyFromTable(rowIndex);

            if (aeKey == null) {
                aeKey = extractAEKeyFromSelectedCpu();
                if (aeKey == null) {
                    aeKey = lastClickedAEKey;
                    if (AE2CraftingLens.isDebugLoggingEnabled()) {
                        AE2CraftingLens.LOGGER.info("Using UI-extracted AEKey: {}", aeKey);
                    }
                } else {
                    if (AE2CraftingLens.isDebugLoggingEnabled()) {
                        AE2CraftingLens.LOGGER.info("Using CPU-extracted AEKey: {}", aeKey);
                    }
                }
            }

            if (aeKey == null) {
                AE2CraftingLens.LOGGER.warn("No AEKey available, sending request without specific item");
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
    
    private Object extractAEKeyFromSlot(Object slot) {
        try {
            try {
                java.lang.reflect.Method getStackMethod = slot.getClass().getMethod("getStack");
                Object stack = getStackMethod.invoke(slot);
                if (stack != null) {
                    java.lang.reflect.Method whatMethod = stack.getClass().getMethod("what");
                    Object aeKey = whatMethod.invoke(stack);
                    if (aeKey != null) {
                        AE2CraftingLens.LOGGER.info("Extracted AEKey from GenericStack: {}", aeKey);
                        return aeKey;
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
                    return aeKey;
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
                    return aeKey;
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error extracting AEKey via what field: {}", e.getMessage());
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error in extractAEKeyFromSlot: {}", e.getMessage());
        }
        return null;
    }
    
    private Object extractAEKeyFromSelectedCpu() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                AE2CraftingLens.LOGGER.debug("Minecraft instance is null");
                return null;
            }
            
            net.minecraft.client.player.LocalPlayer player = mc.player;
            if (player == null) {
                AE2CraftingLens.LOGGER.debug("Player is null");
                return null;
            }
            Object menu = player.containerMenu;
            if (menu == null) {
                AE2CraftingLens.LOGGER.debug("Player has no open menu");
                return null;
            }
            
            String menuClassName = menu.getClass().getName();
            if (!menuClassName.contains("CraftingStatusMenu")) {
                AE2CraftingLens.LOGGER.debug("Menu is not CraftingStatusMenu: {}", menuClassName);
                return null;
            }
            
            AE2CraftingLens.LOGGER.info("Attempting to extract AEKey from CraftingStatusMenu");
            
            java.lang.reflect.Method getSelectedCpuSerialMethod = menu.getClass().getMethod("getSelectedCpuSerial");
            int selectedCpuSerial = (int) getSelectedCpuSerialMethod.invoke(menu);
            
            if (selectedCpuSerial == -1) {
                AE2CraftingLens.LOGGER.debug("No CPU selected");
                return null;
            }
            
            java.lang.reflect.Field cpuListField = menu.getClass().getDeclaredField("cpuList");
            cpuListField.setAccessible(true);
            Object cpuList = cpuListField.get(menu);
            
            java.lang.reflect.Method cpusMethod = cpuList.getClass().getMethod("cpus");
            java.util.List<?> cpus = (java.util.List<?>) cpusMethod.invoke(cpuList);
            
            for (Object cpuEntry : cpus) {
                java.lang.reflect.Method serialMethod = cpuEntry.getClass().getMethod("serial");
                int serial = (int) serialMethod.invoke(cpuEntry);
                
                if (serial == selectedCpuSerial) {
                    java.lang.reflect.Method currentJobMethod = cpuEntry.getClass().getMethod("currentJob");
                    Object currentJob = currentJobMethod.invoke(cpuEntry);
                    
                    if (currentJob == null) {
                        AE2CraftingLens.LOGGER.debug("Selected CPU has no current job");
                        return null;
                    }
                    
                    java.lang.reflect.Method whatMethod = currentJob.getClass().getMethod("what");
                    Object aeKey = whatMethod.invoke(currentJob);
                    
                    if (aeKey != null) {
                        AE2CraftingLens.LOGGER.info("Extracted AEKey from selected CPU {}: {}", serial, aeKey);
                        return aeKey;
                    } else {
                        AE2CraftingLens.LOGGER.debug("GenericStack.what() returned null");
                        return null;
                    }
                }
            }
            
            AE2CraftingLens.LOGGER.debug("Selected CPU serial {} not found in CPU list", selectedCpuSerial);
            return null;

        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error extracting AEKey from selected CPU: {}", e.getMessage());
            return null;
        }
    }

    private Object extractAEKeyFromTable(int rowIndex) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return null;

            Object menu = mc.player.containerMenu;
            if (menu == null) return null;

            String menuClassName = menu.getClass().getName();
            if (!menuClassName.contains("CraftingStatusMenu")) return null;

            AE2CraftingLens.LOGGER.info("Attempting to extract AEKey from menu, rowIndex: {}", rowIndex);

            Object screen = mc.screen;
            if (screen != null) {
                AE2CraftingLens.LOGGER.info("Screen class: {}", screen.getClass().getName());

                try {
                    java.lang.reflect.Field tableField = findFieldInHierarchy(screen.getClass(), "table");
                    if (tableField != null) {
                        tableField.setAccessible(true);
                        Object table = tableField.get(screen);

                        AE2CraftingLens.LOGGER.info("Got 'table' field from screen (found in {}), value: {}", tableField.getDeclaringClass().getName(), table);
                        
                        if (table != null) {
                            AE2CraftingLens.LOGGER.info("Found 'table' field in screen, type: {}", table.getClass().getName());

                            if (table instanceof Object[]) {
                                Object[] array = (Object[]) table;
                                AE2CraftingLens.LOGGER.info("Table array length: {}", array.length);
                                if (rowIndex >= 0 && rowIndex < array.length && array[rowIndex] != null) {
                                    AE2CraftingLens.LOGGER.info("Table item at row {}: {}", rowIndex, array[rowIndex].getClass().getName());
                                    Object key = extractAEKeyFromObject(array[rowIndex], rowIndex);
                                    if (key != null) return key;
                                }
                            } else if (table instanceof java.util.List) {
                                java.util.List<?> list = (java.util.List<?>) table;
                                AE2CraftingLens.LOGGER.info("Table list size: {}", list.size());
                                if (rowIndex >= 0 && rowIndex < list.size() && list.get(rowIndex) != null) {
                                    AE2CraftingLens.LOGGER.info("Table item at row {}: {}", rowIndex, list.get(rowIndex).getClass().getName());
                                    Object key = extractAEKeyFromObject(list.get(rowIndex), rowIndex);
                                    if (key != null) return key;
                                }
                            }
                        }
                    } else {
                        AE2CraftingLens.LOGGER.info("Could not find 'table' field in screen class hierarchy");
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.info("Error getting table from screen: {}", e.getMessage());
                    e.printStackTrace();
                }
            }

            Class<?> clazz = menu.getClass();
            java.util.Set<String> fieldNames = new java.util.HashSet<>();

            while (clazz != null && !clazz.equals(Object.class)) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    fieldNames.add(f.getName());
                }
                clazz = clazz.getSuperclass();
            }

            AE2CraftingLens.LOGGER.info("Menu fields: {}", fieldNames);

            String[] possibleTableFields = {"table", "rows", "items", "craftingItems", "jobItems", "taskItems", "entries", "data", "list"};
            for (String fieldName : possibleTableFields) {
                try {
                    java.lang.reflect.Field field = menu.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(menu);

                    if (fieldValue != null) {
                        AE2CraftingLens.LOGGER.info("Found field '{}' with type: {}", fieldName, fieldValue.getClass().getName());

                        if (fieldValue instanceof Object[]) {
                            Object[] array = (Object[]) fieldValue;
                            if (rowIndex >= 0 && rowIndex < array.length && array[rowIndex] != null) {
                                AE2CraftingLens.LOGGER.info("Found item at index {}: {}", rowIndex, array[rowIndex].getClass().getName());
                                return extractAEKeyFromObject(array[rowIndex], rowIndex);
                            }
                        } else if (fieldValue instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) fieldValue;
                            if (rowIndex >= 0 && rowIndex < list.size() && list.get(rowIndex) != null) {
                                AE2CraftingLens.LOGGER.info("Found item at index {}: {}", rowIndex, list.get(rowIndex).getClass().getName());
                                return extractAEKeyFromObject(list.get(rowIndex), rowIndex);
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }

            AE2CraftingLens.LOGGER.info("Trying to find fields by type...");
            clazz = menu.getClass();
            while (clazz != null && !clazz.equals(Object.class)) {
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(menu);
                        if (val != null) {
                            String typeName = val.getClass().getName();
                            if (typeName.contains("CraftingJob") || typeName.contains("CraftingTask") ||
                                typeName.contains("JobEntry") || typeName.contains("TaskEntry") ||
                                typeName.contains("GenericStack") || typeName.contains("AEItemStack")) {
                                AE2CraftingLens.LOGGER.info("Found potential field '{}' of type: {}", f.getName(), typeName);

                                if (val instanceof Object[]) {
                                    Object[] array = (Object[]) val;
                                    AE2CraftingLens.LOGGER.info("  Array length: {}", array.length);
                                    if (rowIndex >= 0 && rowIndex < array.length && array[rowIndex] != null) {
                                        return extractAEKeyFromObject(array[rowIndex], rowIndex);
                                    }
                                } else if (val instanceof java.util.List) {
                                    java.util.List<?> list = (java.util.List<?>) val;
                                    AE2CraftingLens.LOGGER.info("  List size: {}", list.size());
                                    if (rowIndex >= 0 && rowIndex < list.size() && list.get(rowIndex) != null) {
                                        return extractAEKeyFromObject(list.get(rowIndex), rowIndex);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
                clazz = clazz.getSuperclass();
            }

            AE2CraftingLens.LOGGER.info("Trying screen tableRenderer...");
            try {
                if (screen != null) {
                    AE2CraftingLens.LOGGER.info("Screen class: {}", screen.getClass().getName());

                    java.util.Set<String> screenFieldNames = new java.util.HashSet<>();
                    Class<?> screenClass = screen.getClass();
                    while (screenClass != null && !screenClass.equals(Object.class)) {
                        for (java.lang.reflect.Field f : screenClass.getDeclaredFields()) {
                            screenFieldNames.add(f.getName());
                        }
                        screenClass = screenClass.getSuperclass();
                    }
                    AE2CraftingLens.LOGGER.info("Screen fields: {}", screenFieldNames);

                    for (java.lang.reflect.Field f : screen.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        try {
                            Object val = f.get(screen);
                            if (val != null) {
                                String typeName = val.getClass().getName();
                                if (typeName.contains("Table") || typeName.contains("List") || typeName.contains("Renderer")) {
                                    AE2CraftingLens.LOGGER.info("Screen field '{}' type: {}", f.getName(), typeName);

                                    if (typeName.contains("CraftingStatusTableRenderer") || typeName.contains("TableRenderer")) {
                                        AE2CraftingLens.LOGGER.info("Found tableRenderer, exploring...");
                                        
                                        for (java.lang.reflect.Field tf : val.getClass().getDeclaredFields()) {
                                            tf.setAccessible(true);
                                            try {
                                                Object tfVal = tf.get(val);
                                                if (tfVal != null) {
                                                    String tfTypeName = tfVal.getClass().getName();
                                                    AE2CraftingLens.LOGGER.info("  tableRenderer field '{}' type: {}", tf.getName(), tfTypeName);

                                                    if (tfVal instanceof java.util.List) {
                                                        java.util.List<?> list = (java.util.List<?>) tfVal;
                                                        AE2CraftingLens.LOGGER.info("    List size: {}", list.size());
                                                        if (rowIndex >= 0 && rowIndex < list.size()) {
                                                            Object item = list.get(rowIndex);
                                                            if (item != null) {
                                                                AE2CraftingLens.LOGGER.info("    Item at {}: {}", rowIndex, item.getClass().getName());
                                                                Object key = extractAEKeyFromObject(item, rowIndex);
                                                                if (key != null) return key;
                                                            }
                                                        }
                                                    } else if (tfVal instanceof Object[]) {
                                                        Object[] arr = (Object[]) tfVal;
                                                        AE2CraftingLens.LOGGER.info("    Array length: {}", arr.length);
                                                        if (rowIndex >= 0 && rowIndex < arr.length) {
                                                            Object item = arr[rowIndex];
                                                            if (item != null) {
                                                                AE2CraftingLens.LOGGER.info("    Item at {}: {}", rowIndex, item.getClass().getName());
                                                                Object key = extractAEKeyFromObject(item, rowIndex);
                                                                if (key != null) return key;
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (Exception e) {
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error accessing screen: {}", e.getMessage());
            }

        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error extracting AEKey from table: {}", e.getMessage());
        }

        return null;
    }

    private Object extractAEKeyFromObject(Object obj, int rowIndex) {
        if (obj == null) return null;

        try {
            String className = obj.getClass().getName();
            AE2CraftingLens.LOGGER.info("Extracting AEKey from object: {}", className);

            try {
                java.lang.reflect.Method whatMethod = obj.getClass().getMethod("what");
                Object aeKey = whatMethod.invoke(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via what() at row {}: {}", rowIndex, aeKey);
                    return aeKey;
                }
            } catch (Exception e) {
            }

            try {
                java.lang.reflect.Method getKeyMethod = obj.getClass().getMethod("getKey");
                Object aeKey = getKeyMethod.invoke(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via getKey() at row {}: {}", rowIndex, aeKey);
                    return aeKey;
                }
            } catch (Exception e) {
            }

            try {
                java.lang.reflect.Field whatField = obj.getClass().getDeclaredField("what");
                whatField.setAccessible(true);
                Object aeKey = whatField.get(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via what field at row {}: {}", rowIndex, aeKey);
                    return aeKey;
                }
            } catch (Exception e) {
            }

            try {
                java.lang.reflect.Field keyField = obj.getClass().getDeclaredField("key");
                keyField.setAccessible(true);
                Object aeKey = keyField.get(obj);
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Extracted AEKey via key field at row {}: {}", rowIndex, aeKey);
                    return aeKey;
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
                                    return aeKey;
                                }
                            } else {
                                return val;
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
}
