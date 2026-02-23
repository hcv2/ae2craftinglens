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
        
        // 只在 CraftingStatusScreen 中处理点击
        if (!screenClassName.contains("CraftingStatusScreen")) {
            AE2CraftingLens.LOGGER.info("Not a CraftingStatusScreen, skipping");
            return;
        }
        
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
     * 根据 AE2 的 GUI 布局，物品通常在屏幕中央偏上的位置
     */
    private boolean isClickOnCraftingItem(Object screen, double mouseX, double mouseY) {
        try {
            // 获取屏幕的 GUI 位置和大小
            java.lang.reflect.Method getGuiLeftMethod = screen.getClass().getMethod("getGuiLeft");
            java.lang.reflect.Method getGuiTopMethod = screen.getClass().getMethod("getGuiTop");
            java.lang.reflect.Method getXSizeMethod = screen.getClass().getMethod("getXSize");
            int guiLeft = (int) getGuiLeftMethod.invoke(screen);
            int guiTop = (int) getGuiTopMethod.invoke(screen);
            int xSize = (int) getXSizeMethod.invoke(screen);
            
            // 在 CraftingStatusScreen 中，当前合成物品通常在进度条上方
            // 根据 AE2 的代码，进度条在 y = guiTop + 51 的位置
            // 物品显示在进度条上方，大约在 y = guiTop + 30-35 的位置
            // 物品在屏幕中央，x = guiLeft + xSize/2 - 8 (16x16 物品槽居中)
            int itemCenterX = guiLeft + xSize / 2;
            int itemCenterY = guiTop + 34; // 进度条上方
            int itemSize = 16; // Minecraft 物品槽大小
            
            // 物品区域：16x16 的正方形，居中
            int itemX = itemCenterX - itemSize / 2;
            int itemY = itemCenterY - itemSize / 2;
            int itemWidth = itemSize;
            int itemHeight = itemSize;
            
            // 检查鼠标是否在物品区域内
            boolean isOnItem = mouseX >= itemX && mouseX < itemX + itemWidth && 
                              mouseY >= itemY && mouseY < itemY + itemHeight;
            
            AE2CraftingLens.LOGGER.info("Checking crafting item area: ({}, {}) size ({}, {}), center: ({}, {}), mouse: ({}, {}), result: {}",
                    itemX, itemY, itemWidth, itemHeight, itemCenterX, itemCenterY, mouseX, mouseY, isOnItem);
            
            return isOnItem;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error checking crafting item click: {}", e.getMessage());
            // 如果无法确定，默认返回 false，不触发
            return false;
        }
    }
}
