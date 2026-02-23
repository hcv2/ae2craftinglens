package com.ae2craftinglens.mod;

import com.ae2craftinglens.mod.network.RequestPatternProvidersPacket;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class CraftingScreenHandler {
    
    private Object lastClickedAEKey = null;
    
    public CraftingScreenHandler() {
        AE2CraftingLens.LOGGER.info("CraftingScreenHandler instance created");
    }
    
    @SubscribeEvent
    public void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event received ===");
        // 重置上次点击的 AEKey
        lastClickedAEKey = null;
        
        // 首先检查是否是合成状态屏幕
        Object screen = event.getScreen();
        if (screen == null) {
            AE2CraftingLens.LOGGER.info("Screen is null, skipping");
            return;
        }
        
        String screenClassName = screen.getClass().getName();
        AE2CraftingLens.LOGGER.info("Screen class: {}", screenClassName);
        
        // 在 CraftingStatusScreen 或无线通用终端(WCTScreen)中处理点击
        boolean isCraftingStatusScreen = screenClassName.contains("CraftingStatusScreen");
        boolean isWCTScreen = screenClassName.contains("WCTScreen") && screenClassName.contains("ae2wtlib");
        
        if (!isCraftingStatusScreen && !isWCTScreen) {
            AE2CraftingLens.LOGGER.info("Not a CraftingStatusScreen or WCTScreen, skipping");
            return;
        }
        
        AE2CraftingLens.LOGGER.info("Screen type: {} (CraftingStatusScreen: {}, WCTScreen: {})", 
                isCraftingStatusScreen ? "CraftingStatusScreen" : "WCTScreen", 
                isCraftingStatusScreen, isWCTScreen);
        
        // 检查是否是左键点击
        if (event.getButton() != 0) {
            AE2CraftingLens.LOGGER.info("Not left mouse button, skipping");
            return;
        }
        
        // 检查是否按住Shift
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            AE2CraftingLens.LOGGER.info("Player is null, skipping");
            return;
        }
        
        // 使用更可靠的Shift键检测方法
        boolean isShiftPressed = mc.options.keyShift.isDown();
        AE2CraftingLens.LOGGER.info("Shift key pressed: {}", isShiftPressed);
        
        // 测试模式：暂时允许不按Shift键
        boolean testMode = true;
        AE2CraftingLens.LOGGER.info("Test mode: {}", testMode);
        
        if (!isShiftPressed && !testMode) {
            AE2CraftingLens.LOGGER.info("Shift not pressed and not in test mode, skipping");
            return;
        }
        
        AE2CraftingLens.LOGGER.info("Mouse position: {}, {}", event.getMouseX(), event.getMouseY());
        
        // 检查是否点击在按钮上 - 如果是按钮区域，不处理，让AE2自己处理
        if (isClickOnButton(event)) {
            AE2CraftingLens.LOGGER.info("Click is on a button, letting AE2 handle it");
            return;
        }
        
        // 检查是否点击在当前合成物品上
        if (!isClickOnCraftingItem(screen, event.getMouseX(), event.getMouseY())) {
            AE2CraftingLens.LOGGER.info("Click is not on the crafting item, skipping");
            return;
        }
        
        // 取消事件，防止尝试打开被指向的方块
        event.setCanceled(true);
        AE2CraftingLens.LOGGER.info("Event canceled to prevent opening targeted blocks");
        
        try {
            // 使用提取的 AEKey（可能为 null）
            AE2CraftingLens.LOGGER.info("Using extracted AEKey: {}", lastClickedAEKey);
            RequestPatternProvidersPacket packet = new RequestPatternProvidersPacket(lastClickedAEKey);
            AE2CraftingLens.LOGGER.info("Sending RequestPatternProvidersPacket with AEKey");
            PacketDistributor.sendToServer(packet);
            AE2CraftingLens.LOGGER.info("Packet sent successfully");
            // 重置 AEKey
            lastClickedAEKey = null;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling mouse click", e);
        }
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event processed ===");
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
            
            // 使用反射获取屏幕上的渲染ables列表（包含按钮）
            java.lang.reflect.Field renderablesField = screen.getClass().getDeclaredField("renderables");
            renderablesField.setAccessible(true);
            Iterable<?> renderables = (Iterable<?>) renderablesField.get(screen);
            
            if (renderables != null) {
                for (Object renderable : renderables) {
                    // 检查是否是按钮（AbstractWidget的子类）
                    if (renderable != null && renderable.getClass().getName().contains("Button")) {
                        // 获取按钮的位置和大小
                        java.lang.reflect.Method getXMethod = renderable.getClass().getMethod("getX");
                        java.lang.reflect.Method getYMethod = renderable.getClass().getMethod("getY");
                        java.lang.reflect.Method getWidthMethod = renderable.getClass().getMethod("getWidth");
                        java.lang.reflect.Method getHeightMethod = renderable.getClass().getMethod("getHeight");
                        
                        int x = (int) getXMethod.invoke(renderable);
                        int y = (int) getYMethod.invoke(renderable);
                        int width = (int) getWidthMethod.invoke(renderable);
                        int height = (int) getHeightMethod.invoke(renderable);
                        
                        // 检查鼠标是否在按钮区域内
                        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                            AE2CraftingLens.LOGGER.info("Click detected on button at ({}, {}) size ({}, {})", x, y, width, height);
                            return true;
                        }
                    }
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
                        for (Object renderable : renderables) {
                            if (renderable != null) {
                                String className = renderable.getClass().getName();
                                // 检查是否是物品槽位（类名包含 Slot 或 Item）
                                if (className.contains("Slot") || className.contains("Item")) {
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
                // 对于 CraftingStatusScreen，使用从 AE2 源代码分析得出的精确位置
                itemX = 57;
                itemY = 22;
                itemWidth = 152;
                itemHeight = 16;
                AE2CraftingLens.LOGGER.info("Using precise CraftingStatusScreen detection area based on AE2 source code");
            } else if (isWCTScreen) {
                // 对于 WCTScreen（无线通用终端），根据日志中的鼠标坐标(183.0, 9.0)调整
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
            
            // 检查鼠标是否在备用物品区域内
            boolean isOnItem = relativeX >= itemX && relativeX < itemX + itemWidth && 
                              relativeY >= itemY && relativeY < itemY + itemHeight;
            
            AE2CraftingLens.LOGGER.info("Checking crafting item area: GUI({}, {}), relative mouse: ({}, {}), item area: ({}, {}) size ({}, {}), result: {}",
                    guiLeft, guiTop, relativeX, relativeY, itemX, itemY, itemWidth, itemHeight, isOnItem);
            
            return isOnItem;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error checking crafting item click: {}", e.getMessage());
            // 如果无法确定，默认返回 false，不触发
            return false;
        }
    }
}
