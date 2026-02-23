package com.ae2craftinglens.mod;

import com.ae2craftinglens.mod.network.RequestPatternProvidersPacket;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class CraftingScreenHandler {
    
    public CraftingScreenHandler() {
        AE2CraftingLens.LOGGER.info("CraftingScreenHandler instance created");
    }
    
    @SubscribeEvent
    public void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event received ===");
        
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
            // 简化方案：直接向服务器发送请求，让服务器处理所有逻辑
            // 不再尝试在客户端提取AEKey，而是让服务器查找所有活跃的样板供应器
            RequestPatternProvidersPacket packet = new RequestPatternProvidersPacket(null);
            AE2CraftingLens.LOGGER.info("Sending RequestPatternProvidersPacket (simplified approach)");
            PacketDistributor.sendToServer(packet);
            AE2CraftingLens.LOGGER.info("Packet sent successfully");
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
     * 检查点击是否在当前合成物品上
     * 在 CraftingStatusScreen 中，当前合成物品显示在进度条上方
     * 使用相对于 GUI 左上角的坐标，而不是屏幕坐标
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
            
            // 调试：输出屏幕上的renderables信息
            try {
                java.lang.reflect.Field renderablesField = screen.getClass().getDeclaredField("renderables");
                renderablesField.setAccessible(true);
                Iterable<?> renderables = (Iterable<?>) renderablesField.get(screen);
                if (renderables != null) {
                    int count = 0;
                    for (Object renderable : renderables) {
                        if (renderable != null) {
                            String className = renderable.getClass().getName();
                            // 尝试获取位置信息
                            try {
                                java.lang.reflect.Method getXMethod = renderable.getClass().getMethod("getX");
                                java.lang.reflect.Method getYMethod = renderable.getClass().getMethod("getY");
                                java.lang.reflect.Method getWidthMethod = renderable.getClass().getMethod("getWidth");
                                java.lang.reflect.Method getHeightMethod = renderable.getClass().getMethod("getHeight");
                                int x = (int) getXMethod.invoke(renderable);
                                int y = (int) getYMethod.invoke(renderable);
                                int width = (int) getWidthMethod.invoke(renderable);
                                int height = (int) getHeightMethod.invoke(renderable);
                                AE2CraftingLens.LOGGER.info("Renderable {}: {} at ({}, {}) size ({}, {})", 
                                        count, className, x, y, width, height);
                            } catch (Exception e) {
                                AE2CraftingLens.LOGGER.info("Renderable {}: {} (no position info)", count, className);
                            }
                            count++;
                        }
                    }
                    AE2CraftingLens.LOGGER.info("Total renderables: {}", count);
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error inspecting renderables: {}", e.getMessage());
            }
            
            // 根据屏幕类型确定检测区域
            String screenClassName = screen.getClass().getName();
            boolean isCraftingStatusScreen = screenClassName.contains("CraftingStatusScreen");
            boolean isWCTScreen = screenClassName.contains("WCTScreen") && screenClassName.contains("ae2wtlib");
            
            int itemX, itemY, itemWidth, itemHeight;
            
            if (isCraftingStatusScreen) {
                // 对于 CraftingStatusScreen，使用从 AE2 源代码分析得出的精确位置
                // 表格起始位置: (9, 19)，单元格大小: 67x22，单元格边框: 1
                // 物品在单元格内位置: X偏移+48, Y偏移+3
                // 第一行第一列物品位置: (57, 22)
                // 基于用户点击数据调整，覆盖 X: 57-209, Y: 22-38 (第一行)
                itemX = 57;  // 精确计算的第一列物品X位置
                itemY = 22;  // 精确计算的第一行物品Y位置
                itemWidth = 152;  // 宽度覆盖三列 (57到209)
                itemHeight = 16;  // 标准物品槽高度
                AE2CraftingLens.LOGGER.info("Using precise CraftingStatusScreen detection area based on AE2 source code");
            } else if (isWCTScreen) {
                // 对于 WCTScreen（无线通用终端），暂时使用相同的检测区域
                // 可能需要根据实际布局调整
                itemX = 57;
                itemY = 22;
                itemWidth = 152;
                itemHeight = 16;
                AE2CraftingLens.LOGGER.info("Using WCTScreen detection area (same as CraftingStatusScreen for now)");
            } else {
                // 未知屏幕类型，使用基于用户点击数据的区域
                itemX = 70;
                itemY = 29;
                itemWidth = 130;
                itemHeight = 22;
                AE2CraftingLens.LOGGER.info("Using default detection area for unknown screen type");
            }
            
            // 检查鼠标是否在物品区域内
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
