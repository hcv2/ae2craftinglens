package com.ae2craftinglens.mod;

import java.lang.reflect.Method;

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
        try {
            Object screen = event.getScreen();
            if (screen == null) {
                return;
            }
            
            String screenClassName = screen.getClass().getName();
            
            // 检查是否是合成相关的屏幕，包括无线终端的情况
            if (!screenClassName.contains("Crafting") && !screenClassName.contains("crafting")) {
                return;
            }
            
            // 检查是否是左键点击
            if (event.getButton() != 0) {
                return;
            }
            
            // 检查是否按住Shift
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            
            // 使用更可靠的Shift键检测方法
            boolean isShiftPressed = mc.options.keyShift.isDown();
            
            // 测试模式：暂时允许不按Shift键
            boolean testMode = true;
            
            if (!isShiftPressed && !testMode) {
                return;
            }
            
            // 尝试获取鼠标下的物品
            Object hoveredStack = null;
            
            // 方法1: 尝试从父类AbstractContainerScreen获取hoveredSlot
            try {
                Class<?> containerScreenClass = Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");
                java.lang.reflect.Field hoveredSlotField = containerScreenClass.getDeclaredField("hoveredSlot");
                hoveredSlotField.setAccessible(true);
                Object hoveredSlot = hoveredSlotField.get(screen);
                if (hoveredSlot != null) {
                    try {
                        Method getItemMethod = hoveredSlot.getClass().getMethod("getItem");
                        hoveredStack = getItemMethod.invoke(hoveredSlot);
                    } catch (Exception e) {
                        // 静默失败
                    }
                }
            } catch (Exception e) {
                // 静默失败
            }
            
            // 方法2: 尝试使用getSlotAt方法
            if (hoveredStack == null) {
                try {
                    Method method = screen.getClass().getMethod("getSlotAt", double.class, double.class);
                    Object slot = method.invoke(screen, event.getMouseX(), event.getMouseY());
                    if (slot != null) {
                        try {
                            Method getStackMethod = slot.getClass().getMethod("getItem");
                            hoveredStack = getStackMethod.invoke(slot);
                        } catch (Exception e) {
                            // 静默失败
                        }
                    }
                } catch (Exception e) {
                    // 静默失败
                }
            }
            
            if (hoveredStack != null) {
                // 尝试获取AEKey
                Object aeKey = null;
                
                // 方法1: 直接检查是否是AEKey
                try {
                    Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                    if (aeKeyClass.isInstance(hoveredStack)) {
                        aeKey = hoveredStack;
                    }
                } catch (ClassNotFoundException e) {
                    AE2CraftingLens.LOGGER.error("AEKey class not found", e);
                }
                
                // 方法2: 尝试从stack方法获取
                if (aeKey == null) {
                    try {
                        Method stackMethod = hoveredStack.getClass().getMethod("stack");
                        Object stack = stackMethod.invoke(hoveredStack);
                        if (stack != null) {
                            try {
                                Method whatMethod = stack.getClass().getMethod("what");
                                aeKey = whatMethod.invoke(stack);
                            } catch (Exception e) {
                                // 静默失败
                            }
                        }
                    } catch (Exception e) {
                        // 静默失败
                    }
                }
                
                // 方法3: 尝试从getType方法获取
                if (aeKey == null) {
                    try {
                        Method getTypeMethod = hoveredStack.getClass().getMethod("getType");
                        aeKey = getTypeMethod.invoke(hoveredStack);
                    } catch (Exception e) {
                        // 静默失败
                    }
                }
                
                // 方法4: 尝试从getItem方法获取并转换为AEKey
                if (aeKey == null) {
                    try {
                        Method getItemMethod = hoveredStack.getClass().getMethod("getItem");
                        Object item = getItemMethod.invoke(hoveredStack);
                        if (item != null) {
                            try {
                                Class<?> aeApiClass = Class.forName("appeng.api.stacks.AEApi");
                                Method getKeyMethod = aeApiClass.getMethod("key");
                                Object keyHelper = getKeyMethod.invoke(null);
                                Method ofItemMethod = keyHelper.getClass().getMethod("of", net.minecraft.world.item.ItemStack.class);
                                aeKey = ofItemMethod.invoke(keyHelper, hoveredStack);
                            } catch (Exception e) {
                                // 静默失败
                            }
                        }
                    } catch (Exception e) {
                        // 静默失败
                    }
                }
                
                if (aeKey != null) {
                    try {
                        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                        if (aeKeyClass.isInstance(aeKey)) {
                            RequestPatternProvidersPacket packet = new RequestPatternProvidersPacket(aeKey);
                            PacketDistributor.sendToServer(packet);
                            event.setCanceled(true);
                        }
                    } catch (ClassNotFoundException e) {
                        AE2CraftingLens.LOGGER.error("AEKey class not found", e);
                    }
                }
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling mouse click", e);
        }
    }
}
