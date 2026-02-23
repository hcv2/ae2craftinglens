package com.ae2craftinglens.mod;

import com.ae2craftinglens.mod.network.RequestPatternProvidersPacket;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class CraftingScreenHandler {
    
    private Object lastClickedAEKey = null;
    
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
        // 重置上次点击的 AEKey
        lastClickedAEKey = null;
        
        // 首先检查是否是合成状态屏幕
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
        
        // 在 CraftingStatusScreen 或无线通用终端(WCTScreen)中处理点击
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
        
        // 检查是否是左键点击
        if (event.getButton() != 0) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Not left mouse button, skipping");
            }
            return;
        }
        
        // 检查是否按住Shift
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Player is null, skipping");
            }
            return;
        }
        
        // 使用多种方法检测Shift键，确保可靠性
        boolean isShiftPressed = false;
        
        // 方法1: 尝试使用事件本身的hasShiftDown方法（如果可用）
        try {
            java.lang.reflect.Method hasShiftDownMethod = event.getClass().getMethod("hasShiftDown");
            isShiftPressed = (boolean) hasShiftDownMethod.invoke(event);
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Shift detection via event.hasShiftDown(): {}", isShiftPressed);
            }
        } catch (Exception e) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.debug("Event.hasShiftDown() not available: {}", e.getMessage());
            }
        }
        
        // 方法2: 如果方法1失败，尝试使用屏幕的hasShiftDown方法
        if (!isShiftPressed) {
            try {
                // 使用已存在的screen变量
                java.lang.reflect.Method hasShiftDownMethod = screen.getClass().getMethod("hasShiftDown");
                isShiftPressed = (boolean) hasShiftDownMethod.invoke(screen);
                if (AE2CraftingLens.isDebugLoggingEnabled()) {
                    AE2CraftingLens.LOGGER.info("Shift detection via screen.hasShiftDown(): {}", isShiftPressed);
                }
            } catch (Exception e) {
                if (AE2CraftingLens.isDebugLoggingEnabled()) {
                    AE2CraftingLens.LOGGER.debug("Screen.hasShiftDown() not available: {}", e.getMessage());
                }
            }
        }
        
        // 方法3: 回退到原始的键位绑定检测
        if (!isShiftPressed) {
            isShiftPressed = mc.options.keyShift.isDown();
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Shift detection via key binding: {}", isShiftPressed);
            }
        }
        
        // 方法4: 检查键盘状态（最底层的检测）
        if (!isShiftPressed) {
            try {
                // 使用Minecraft的Keyboard类检查Shift键状态
                Class<?> keyboardClass = Class.forName("com.mojang.blaze3d.platform.InputConstants");
                java.lang.reflect.Method isKeyDownMethod = keyboardClass.getMethod("isKeyDown", int.class);
                // Shift键的键盘码：左Shift=340，右Shift=344
                boolean leftShift = (boolean) isKeyDownMethod.invoke(null, 340);
                boolean rightShift = (boolean) isKeyDownMethod.invoke(null, 344);
                isShiftPressed = leftShift || rightShift;
                if (AE2CraftingLens.isDebugLoggingEnabled()) {
                    AE2CraftingLens.LOGGER.info("Shift detection via raw keyboard (L:{}, R:{}): {}", leftShift, rightShift, isShiftPressed);
                }
            } catch (Exception e) {
                if (AE2CraftingLens.isDebugLoggingEnabled()) {
                    AE2CraftingLens.LOGGER.debug("Raw keyboard detection failed: {}", e.getMessage());
                }
            }
        }
        
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Final shift key pressed: {}", isShiftPressed);
        }
        
        // 测试模式：暂时允许不按Shift键
        boolean testMode = false;
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Test mode: {}", testMode);
        }
        
        if (!isShiftPressed && !testMode) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Shift not pressed and not in test mode, skipping");
            }
            return;
        }
        
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Mouse position: {}, {}", event.getMouseX(), event.getMouseY());
        }
        
        // 检查是否点击在按钮上 - 如果是按钮区域，不处理，让AE2自己处理
        if (isClickOnButton(event)) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Click is on a button, letting AE2 handle it");
            }
            return;
        }
        
        // 检查是否点击在当前合成物品上
        if (!isClickOnCraftingItem(screen, event.getMouseX(), event.getMouseY())) {
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Click is not on the crafting item, skipping");
            }
            return;
        }
        
        // 取消事件，防止尝试打开被指向的方块
        event.setCanceled(true);
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("Event canceled to prevent opening targeted blocks");
        }
        
        try {
            // 优先从选中的CPU提取AEKey（减少UI反射依赖）
            Object aeKey = extractAEKeyFromSelectedCpu();
            if (aeKey == null) {
                // 回退到UI反射提取的AEKey
                aeKey = lastClickedAEKey;
                if (AE2CraftingLens.isDebugLoggingEnabled()) {
                    AE2CraftingLens.LOGGER.info("Using UI-extracted AEKey: {}", aeKey);
                }
            } else {
                if (AE2CraftingLens.isDebugLoggingEnabled()) {
                    AE2CraftingLens.LOGGER.info("Using CPU-extracted AEKey: {}", aeKey);
                }
            }
            
            if (aeKey == null) {
                AE2CraftingLens.LOGGER.warn("No AEKey available, sending request without specific item");
            }
            
            RequestPatternProvidersPacket packet = new RequestPatternProvidersPacket(aeKey);
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Sending RequestPatternProvidersPacket with AEKey");
            }
            PacketDistributor.sendToServer(packet);
            if (AE2CraftingLens.isDebugLoggingEnabled()) {
                AE2CraftingLens.LOGGER.info("Packet sent successfully");
            }
            // 重置 AEKey
            lastClickedAEKey = null;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling mouse click", e);
        }
        if (AE2CraftingLens.isDebugLoggingEnabled()) {
            AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event processed ===");
        }
    }
    
    /**
     * 检查点击是否在按钮上
     * 通过检查屏幕上的子组件（按钮）来判断
     */
    private boolean isClickOnButton(ScreenEvent.MouseButtonPressed.Pre event) {
        try {
            Object screen = event.getScreen();
            double mouseX = event.getMouseX();
            double mouseY = event.getMouseY();
            
            // 尝试从多个可能的字段中获取组件列表
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
                                // 扩展按钮检测范围：包括各种按钮和组件类型
                                boolean isButton = className.contains("Button") || 
                                                   className.contains("Widget") ||
                                                   className.contains("ImageButton") ||
                                                   className.contains("IconButton") ||
                                                   className.contains("TextButton") ||
                                                   className.contains("Pressable") ||
                                                   className.contains("Clickable");
                                
                                if (isButton) {
                                    // 获取按钮的位置和大小
                                    try {
                                        java.lang.reflect.Method getXMethod = component.getClass().getMethod("getX");
                                        java.lang.reflect.Method getYMethod = component.getClass().getMethod("getY");
                                        java.lang.reflect.Method getWidthMethod = component.getClass().getMethod("getWidth");
                                        java.lang.reflect.Method getHeightMethod = component.getClass().getMethod("getHeight");
                                        
                                        int x = (int) getXMethod.invoke(component);
                                        int y = (int) getYMethod.invoke(component);
                                        int width = (int) getWidthMethod.invoke(component);
                                        int height = (int) getHeightMethod.invoke(component);
                                        
                                        // 检查鼠标是否在按钮区域内
                                        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                                            AE2CraftingLens.LOGGER.info("Click detected on button at ({}, {}) size ({}, {})", x, y, width, height);
                                            
                                            // 检查按钮文本是否与取消合成相关
                                            try {
                                                java.lang.reflect.Method getMessageMethod = component.getClass().getMethod("getMessage");
                                                Object message = getMessageMethod.invoke(component);
                                                if (message != null) {
                                                    String messageStr = message.toString().toLowerCase();
                                                    if (messageStr.contains("cancel") || messageStr.contains("stop") || 
                                                        messageStr.contains("x") || messageStr.contains("删除") ||
                                                        messageStr.contains("取消") || messageStr.contains("中止")) {
                                                        AE2CraftingLens.LOGGER.info("Button appears to be a cancel button: {}", message);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // 忽略，不是所有按钮都有getMessage方法
                                            }
                                            
                                            // 检查按钮是否有特定的标签或纹理标识
                                            try {
                                                java.lang.reflect.Field iconField = component.getClass().getDeclaredField("icon");
                                                iconField.setAccessible(true);
                                                Object icon = iconField.get(component);
                                                if (icon != null && icon.toString().contains("cancel")) {
                                                    AE2CraftingLens.LOGGER.info("Button has cancel icon");
                                                }
                                            } catch (Exception e) {
                                                // 忽略
                                            }
                                            
                                            return true;
                                        }
                                    } catch (Exception e) {
                                        // 如果无法获取位置信息，跳过此按钮
                                        AE2CraftingLens.LOGGER.debug("Error getting button position for {}: {}", className, e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 字段不存在，继续尝试下一个
                    AE2CraftingLens.LOGGER.debug("Field {} not found: {}", fieldName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            // 如果反射失败，记录错误但不阻止事件处理
            AE2CraftingLens.LOGGER.debug("Error checking button click: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 尝试从槽位对象中提取 AEKey
     */
    private Object extractAEKeyFromSlot(Object slot) {
        try {
            // 方法1: 尝试获取 GenericStack，然后获取 what 字段
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
            
            // 方法2: 尝试直接获取 AEKey 字段
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
            
            // 方法3: 尝试获取 what 字段
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
    
    /**
     * 从当前选中的合成CPU提取AEKey
     * 通过直接访问CraftingStatusMenu，减少对UI反射的依赖
     */
    private Object extractAEKeyFromSelectedCpu() {
        try {
            // 获取Minecraft实例和玩家
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                AE2CraftingLens.LOGGER.debug("Minecraft or player not available");
                return null;
            }
            
            Object menu = mc.player.containerMenu;
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
            
            // 获取选中的CPU序列号
            java.lang.reflect.Method getSelectedCpuSerialMethod = menu.getClass().getMethod("getSelectedCpuSerial");
            int selectedCpuSerial = (int) getSelectedCpuSerialMethod.invoke(menu);
            
            if (selectedCpuSerial == -1) {
                AE2CraftingLens.LOGGER.debug("No CPU selected");
                return null;
            }
            
            // 获取CPU列表
            java.lang.reflect.Field cpuListField = menu.getClass().getDeclaredField("cpuList");
            cpuListField.setAccessible(true);
            Object cpuList = cpuListField.get(menu);
            
            // 获取cpus列表
            java.lang.reflect.Method cpusMethod = cpuList.getClass().getMethod("cpus");
            java.util.List<?> cpus = (java.util.List<?>) cpusMethod.invoke(cpuList);
            
            // 查找选中的CPU条目
            for (Object cpuEntry : cpus) {
                // 获取serial字段
                java.lang.reflect.Method serialMethod = cpuEntry.getClass().getMethod("serial");
                int serial = (int) serialMethod.invoke(cpuEntry);
                
                if (serial == selectedCpuSerial) {
                    // 获取currentJob字段
                    java.lang.reflect.Method currentJobMethod = cpuEntry.getClass().getMethod("currentJob");
                    Object currentJob = currentJobMethod.invoke(cpuEntry);
                    
                    if (currentJob == null) {
                        AE2CraftingLens.LOGGER.debug("Selected CPU has no current job");
                        return null;
                    }
                    
                    // 从GenericStack提取AEKey
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
    
    /**
     * 检查点击是否在当前合成物品上
     * 通过检查屏幕上的所有可渲染对象，精确识别合成物品槽位
     */
    private boolean isClickOnCraftingItem(Object screen, double mouseX, double mouseY) {
        try {
            // 获取屏幕的 GUI 位置和大小
            java.lang.reflect.Method getGuiLeftMethod = screen.getClass().getMethod("getGuiLeft");
            java.lang.reflect.Method getGuiTopMethod = screen.getClass().getMethod("getGuiTop");
            int guiLeft = (int) getGuiLeftMethod.invoke(screen);
            int guiTop = (int) getGuiTopMethod.invoke(screen);
            
            // 将鼠标坐标转换为相对于 GUI 左上角的坐标
            double relativeX = mouseX - guiLeft;
            double relativeY = mouseY - guiTop;
            
            // 检查屏幕类型
            String screenClassName = screen.getClass().getName();
            boolean isCraftingStatusScreen = screenClassName.contains("CraftingStatusScreen");
            boolean isWCTScreen = screenClassName.contains("WCTScreen") && screenClassName.contains("ae2wtlib");
            
            // 方法1: 尝试通过反射查找合成物品槽位
            if (isCraftingStatusScreen || isWCTScreen) {
                try {
                    java.lang.reflect.Field renderablesField = screen.getClass().getDeclaredField("renderables");
                    renderablesField.setAccessible(true);
                    Iterable<?> renderables = (Iterable<?>) renderablesField.get(screen);
                    
                    if (renderables != null) {
                        int renderableCount = 0;
                        java.util.List<String> renderableClassNames = new java.util.ArrayList<>();
                        for (Object renderable : renderables) {
                            renderableCount++;
                            if (renderable != null) {
                                String className = renderable.getClass().getName();
                                renderableClassNames.add(className);
                                
                                // 记录前几个renderable的详细信息用于调试
                                if (renderableCount <= 10) {
                                    AE2CraftingLens.LOGGER.debug("Renderable {}: {}", renderableCount, className);
                                    try {
                                        java.lang.reflect.Method getXMethod = renderable.getClass().getMethod("getX");
                                        java.lang.reflect.Method getYMethod = renderable.getClass().getMethod("getY");
                                        int x = (int) getXMethod.invoke(renderable);
                                        int y = (int) getYMethod.invoke(renderable);
                                        AE2CraftingLens.LOGGER.debug("  Position: ({}, {})", x, y);
                                    } catch (Exception e) {
                                        AE2CraftingLens.LOGGER.debug("  No position info");
                                    }
                                }
                                
                                // 检查是否是物品槽位（类名包含 Slot, Item, Crafting, Job, Task 等）
                                if (className.contains("Slot") || className.contains("Item") || 
                                    className.contains("Crafting") || className.contains("Job") || 
                                    className.contains("Task") || className.contains("Stack") ||
                                    className.contains("Renderable") && className.contains("Crafting")) {
                                    // 检查是否具有位置信息
                                    try {
                                        java.lang.reflect.Method getXMethod = renderable.getClass().getMethod("getX");
                                        java.lang.reflect.Method getYMethod = renderable.getClass().getMethod("getY");
                                        java.lang.reflect.Method getWidthMethod = renderable.getClass().getMethod("getWidth");
                                        java.lang.reflect.Method getHeightMethod = renderable.getClass().getMethod("getHeight");
                                        int x = (int) getXMethod.invoke(renderable);
                                        int y = (int) getYMethod.invoke(renderable);
                                        int width = (int) getWidthMethod.invoke(renderable);
                                        int height = (int) getHeightMethod.invoke(renderable);
                                        
                                        // 检查鼠标是否在该槽位区域内
                                        if (relativeX >= x && relativeX < x + width && relativeY >= y && relativeY < y + height) {
                                            // 进一步检查该槽位是否包含物品（可选）
                                            // 如果有 getItem 方法，可以检查是否非空
                                            try {
                                                java.lang.reflect.Method getItemMethod = renderable.getClass().getMethod("getItem");
                                                Object item = getItemMethod.invoke(renderable);
                                                if (item != null) {
                                                    AE2CraftingLens.LOGGER.info("Click detected on crafting item slot with item at ({}, {}) size ({}, {})", 
                                                            x, y, width, height);
                                                    // 尝试提取 AEKey
                                                    lastClickedAEKey = extractAEKeyFromSlot(renderable);
                                                    return true;
                                                } else {
                                                    AE2CraftingLens.LOGGER.info("Click on empty slot, skipping");
                                                    return false;
                                                }
                                            } catch (Exception e) {
                                                // 如果没有 getItem 方法，仍然认为是物品槽位
                                                AE2CraftingLens.LOGGER.info("Click detected on potential crafting item slot at ({}, {}) size ({}, {})", 
                                                        x, y, width, height);
                                                // 尝试提取 AEKey
                                                lastClickedAEKey = extractAEKeyFromSlot(renderable);
                                                return true;
                                            }
                                        }
                                    } catch (Exception e) {
                                        // 忽略没有位置信息的槽位
                                        AE2CraftingLens.LOGGER.debug("Slot without position info: {}", className);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error inspecting renderables: {}", e.getMessage());
                }
            }
            
            // 方法2: 如果反射方法失败，使用基于屏幕类型的备用检测区域
            int itemX, itemY, itemWidth, itemHeight;
            
            if (isCraftingStatusScreen) {
                // 对于 CraftingStatusScreen，使用从 AE2 源代码分析得出的精确表格位置
                // 表格起始位置: (9, 19) (来自 CraftingCPUScreen.java: new CraftingStatusTableRenderer(this, 9, 19))
                // 单元格宽度: 67, 高度: 22, 列数: 3, 单元格边框: 1
                // 表格宽度: 3 * (67 + 1) = 204
                // 使用表格区域进行检测，后续会进一步精确定位到具体单元格
                itemX = 9;
                itemY = 19;
                itemWidth = 204;
                itemHeight = 22; // 单个单元格高度
                AE2CraftingLens.LOGGER.info("Using precise CraftingStatusScreen detection area based on AE2 source code (table area)");
            } else if (isWCTScreen) {
                // 对于 WCTScreen（无线通用终端），根据日志中的鼠标坐标(183.0, 9.0)调整
                // 无线终端可能使用不同的布局，暂时保持原有值
                itemX = 57;
                itemY = 9;
                itemWidth = 152;
                itemHeight = 16;
                AE2CraftingLens.LOGGER.info("Using adjusted WCTScreen detection area based on click coordinates");
            } else {
                // 未知屏幕类型，使用基于用户点击数据的区域
                itemX = 70;
                itemY = 29;
                itemWidth = 130;
                itemHeight = 22;
                AE2CraftingLens.LOGGER.info("Using default detection area for unknown screen type");
            }
            
            // 检查鼠标是否在备用物品区域内 - 支持多行制作任务和滚动
            boolean isOnItem = false;
            // 单元格高度22 + 边框1 = 23像素行高
            int rowHeight = 23;
            
            // 支持最多20行制作任务（考虑滚动和大量任务）
            int maxRows = 20;
            
            // 计算相对于第一行的垂直偏移
            float deltaY = (float)(relativeY - itemY);
            
            // 计算最接近的行索引（考虑滚动）
            int closestRow = Math.round(deltaY / rowHeight);
            
            // 确保行索引在合理范围内
            if (closestRow < 0) closestRow = 0;
            if (closestRow >= maxRows) closestRow = maxRows - 1;
            
            // 检查最接近的行及其相邻行（容差±2行）
            int startRow = Math.max(0, closestRow - 2);
            int endRow = Math.min(maxRows - 1, closestRow + 2);
            
            AE2CraftingLens.LOGGER.debug("DeltaY: {}, closestRow: {}, checking rows {} to {}", deltaY, closestRow, startRow, endRow);
            
            for (int row = startRow; row <= endRow; row++) {
                int currentItemY = itemY + row * rowHeight;
                // 单元格宽度67，列数3，边框1，所以每个单元格宽度68，但整个表格宽度是204
                // 检查鼠标是否在表格宽度内
                boolean isOnCurrentRow = relativeX >= itemX && relativeX < itemX + itemWidth && 
                                        relativeY >= currentItemY && relativeY < currentItemY + itemHeight;
                
                if (isOnCurrentRow) {
                    // 进一步计算点击在哪个列（单元格）内
                    int cellXOffset = (int)(relativeX - itemX);
                    int cellCol = cellXOffset / 68; // 单元格宽度+边框
                    if (cellCol >= 0 && cellCol < 3) {
                        isOnItem = true;
                        AE2CraftingLens.LOGGER.info("Click detected on crafting item row {}, col {} at y={} (deltaY={})", row, cellCol, currentItemY, deltaY);
                        break;
                    }
                }
            }
            
            // 如果没找到，记录详细信息用于调试
            if (!isOnItem) {
                AE2CraftingLens.LOGGER.debug("Click not detected on any crafting row. Details: relativeX={}, relativeY={}, itemX={}, itemY={}, rowHeight={}, deltaY={}, closestRow={}", 
                        relativeX, relativeY, itemX, itemY, rowHeight, deltaY, closestRow);
            }
            
            AE2CraftingLens.LOGGER.info("Checking crafting item area: GUI({}, {}), relative mouse: ({}, {}), table area: ({}, {}) size ({}, {}), cell size: 67x22, rows checked: {}-{}, result: {}",
                    guiLeft, guiTop, relativeX, relativeY, itemX, itemY, itemWidth, itemHeight, startRow, endRow, isOnItem);
            
            return isOnItem;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error checking crafting item click: {}", e.getMessage());
            // 如果无法确定，默认返回 false，不触发
            return false;
        }
    }
}
