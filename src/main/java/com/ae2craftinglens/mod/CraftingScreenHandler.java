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
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event received ===");
        
        // 首先检查是否是合成相关的屏幕，包括无线终端的情况
        Object screen = event.getScreen();
        if (screen == null) {
            AE2CraftingLens.LOGGER.info("Screen is null, skipping");
            return;
        }
        
        String screenClassName = screen.getClass().getName();
        AE2CraftingLens.LOGGER.info("Screen class: {}", screenClassName);
        
        if (!screenClassName.contains("Crafting") && !screenClassName.contains("crafting")) {
            AE2CraftingLens.LOGGER.info("Not a crafting screen, skipping");
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
        
        // 先取消事件，防止尝试打开被指向的方块
        event.setCanceled(true);
        AE2CraftingLens.LOGGER.info("Event canceled to prevent opening targeted blocks");
        AE2CraftingLens.LOGGER.info("Mouse position: {}, {}", event.getMouseX(), event.getMouseY());
        
        try {
            // 尝试获取鼠标下的物品
            Object hoveredStack = null;
            
            // 方法1: 尝试从父类AbstractContainerScreen获取hoveredSlot
            try {
                Class<?> containerScreenClass = Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");
                java.lang.reflect.Field hoveredSlotField = containerScreenClass.getDeclaredField("hoveredSlot");
                hoveredSlotField.setAccessible(true);
                Object hoveredSlot = hoveredSlotField.get(screen);
                if (hoveredSlot != null) {
                    AE2CraftingLens.LOGGER.info("Found hoveredSlot: {}", hoveredSlot);
                    try {
                        Method getItemMethod = hoveredSlot.getClass().getMethod("getItem");
                        hoveredStack = getItemMethod.invoke(hoveredSlot);
                        AE2CraftingLens.LOGGER.info("Found hoveredStack via hoveredSlot: {}", hoveredStack);
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.error("Error getting item from hoveredSlot: {}", e.getMessage());
                    }
                } else {
                    AE2CraftingLens.LOGGER.info("No hoveredSlot found");
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error getting hoveredSlot: {}", e.getMessage());
            }
            
            // 方法2: 尝试使用getSlotAt方法
            if (hoveredStack == null) {
                try {
                    Method method = screen.getClass().getMethod("getSlotAt", double.class, double.class);
                    Object slot = method.invoke(screen, event.getMouseX(), event.getMouseY());
                    if (slot != null) {
                        AE2CraftingLens.LOGGER.info("Found slot via getSlotAt: {}", slot);
                        try {
                            Method getStackMethod = slot.getClass().getMethod("getItem");
                            hoveredStack = getStackMethod.invoke(slot);
                            AE2CraftingLens.LOGGER.info("Found hoveredStack via getSlotAt: {}", hoveredStack);
                        } catch (Exception e) {
                            AE2CraftingLens.LOGGER.error("Error getting item from slot: {}", e.getMessage());
                        }
                    } else {
                        AE2CraftingLens.LOGGER.info("No slot found at mouse position: {}, {}", event.getMouseX(), event.getMouseY());
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.error("Error calling getSlotAt: {}", e.getMessage());
                    
                    // 尝试处理CraftingStatusScreen
                    if (screenClassName.contains("CraftingStatusScreen")) {
                        AE2CraftingLens.LOGGER.info("Attempting special handling for CraftingStatusScreen");
                        try {
                            // 尝试获取CraftingStatusScreen中的物品列表
                            // 首先尝试获取所有字段，查看有哪些可能包含物品的字段
                            AE2CraftingLens.LOGGER.info("Listing all fields in CraftingStatusScreen:");
                            for (java.lang.reflect.Field field : screen.getClass().getDeclaredFields()) {
                                field.setAccessible(true);
                                try {
                                    Object value = field.get(screen);
                                    if (value != null) {
                                        AE2CraftingLens.LOGGER.info("Field: {} = {} (Type: {})", field.getName(), value, value.getClass().getName());
                                    }
                                } catch (Exception ex) {
                                    // 忽略
                                }
                            }
                            
                            // 尝试获取CraftingStatusScreen的父类字段
                            Class<?> superClass = screen.getClass().getSuperclass();
                            while (superClass != null) {
                                AE2CraftingLens.LOGGER.info("Listing fields in superclass: {}", superClass.getName());
                                for (java.lang.reflect.Field field : superClass.getDeclaredFields()) {
                                    field.setAccessible(true);
                                    try {
                                        Object value = field.get(screen);
                                        if (value != null) {
                                            AE2CraftingLens.LOGGER.info("Field: {} = {} (Type: {})", field.getName(), value, value.getClass().getName());
                                        }
                                    } catch (Exception ex) {
                                        // 忽略
                                    }
                                }
                                superClass = superClass.getSuperclass();
                            }
                            
                            // 尝试从container字段获取物品信息
                            try {
                                java.lang.reflect.Field containerField = screen.getClass().getDeclaredField("container");
                                containerField.setAccessible(true);
                                Object container = containerField.get(screen);
                                if (container != null) {
                                    AE2CraftingLens.LOGGER.info("Found container: {} (Type: {})", container, container.getClass().getName());
                                    
                                    // 尝试获取container中的slots或其他可能包含物品的字段
                                    String[] containerFieldNames = {"slots", "jobList", "craftingJobs", "tasks", "items"};
                                    for (String fieldName : containerFieldNames) {
                                        try {
                                            java.lang.reflect.Field field = container.getClass().getDeclaredField(fieldName);
                                            field.setAccessible(true);
                                            Object value = field.get(container);
                                            if (value != null) {
                                                AE2CraftingLens.LOGGER.info("Found container field: {} = {} (Type: {})", fieldName, value, value.getClass().getName());
                                                
                                                // 尝试检查是否是可迭代的列表
                                                if (value instanceof Iterable) {
                                                    AE2CraftingLens.LOGGER.info("Container field {} is Iterable", fieldName);
                                                    // 这里可以进一步处理迭代器，找到鼠标位置对应的物品
                                                }
                                            }
                                        } catch (Exception ex) {
                                            // 忽略，尝试下一个字段名
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                AE2CraftingLens.LOGGER.error("Error accessing container field: {}", ex.getMessage());
                            }
                            
                            // 尝试常见的字段名
                            String[] possibleFieldNames = {
                                "jobs", "craftingJobs", "jobList", "tasks", "craftingTasks",
                                "list", "itemList", "craftingList", "recipeList", "grid",
                                "menu", "container", "inventory", "slots", "slotList"
                            };
                            
                            for (String fieldName : possibleFieldNames) {
                                try {
                                    java.lang.reflect.Field field = screen.getClass().getDeclaredField(fieldName);
                                    field.setAccessible(true);
                                    Object value = field.get(screen);
                                    if (value != null) {
                                        AE2CraftingLens.LOGGER.info("Found potential field: {} = {} (Type: {})", fieldName, value, value.getClass().getName());
                                        
                                        // 尝试检查是否是可迭代的列表
                                        if (value instanceof Iterable) {
                                            AE2CraftingLens.LOGGER.info("Field {} is Iterable", fieldName);
                                            // 这里可以进一步处理迭代器，找到鼠标位置对应的物品
                                        }
                                    }
                                } catch (Exception ex) {
                                    // 忽略，尝试下一个字段名
                                }
                            }
                        } catch (Exception ex) {
                            AE2CraftingLens.LOGGER.error("Error handling CraftingStatusScreen: {}", ex.getMessage());
                        }
                    }
                }
            }
            
            if (hoveredStack != null) {
                AE2CraftingLens.LOGGER.info("Processing hoveredStack: {}", hoveredStack);
                // 尝试获取AEKey
                Object aeKey = null;
                
                // 方法1: 直接检查是否是AEKey
                try {
                    Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                    if (aeKeyClass.isInstance(hoveredStack)) {
                        aeKey = hoveredStack;
                        AE2CraftingLens.LOGGER.info("AEKey found directly: {}", aeKey);
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
                            AE2CraftingLens.LOGGER.info("Found stack: {}", stack);
                            try {
                                Method whatMethod = stack.getClass().getMethod("what");
                                aeKey = whatMethod.invoke(stack);
                                AE2CraftingLens.LOGGER.info("AEKey found via stack().what(): {}", aeKey);
                            } catch (Exception e) {
                                AE2CraftingLens.LOGGER.error("Error getting what() from stack: {}", e.getMessage());
                            }
                        } else {
                            AE2CraftingLens.LOGGER.info("stack() returned null");
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.error("Error calling stack() method: {}", e.getMessage());
                    }
                }
                
                // 方法3: 尝试从getType方法获取
                if (aeKey == null) {
                    try {
                        Method getTypeMethod = hoveredStack.getClass().getMethod("getType");
                        aeKey = getTypeMethod.invoke(hoveredStack);
                        AE2CraftingLens.LOGGER.info("AEKey found via getType(): {}", aeKey);
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.error("Error calling getType() method: {}", e.getMessage());
                    }
                }
                
                // 方法4: 尝试从getItem方法获取并转换为AEKey
                if (aeKey == null) {
                    try {
                        Method getItemMethod = hoveredStack.getClass().getMethod("getItem");
                        Object item = getItemMethod.invoke(hoveredStack);
                        if (item != null) {
                            AE2CraftingLens.LOGGER.info("Found item: {}", item);
                            try {
                                Class<?> aeApiClass = Class.forName("appeng.api.stacks.AEApi");
                                Method getKeyMethod = aeApiClass.getMethod("key");
                                Object keyHelper = getKeyMethod.invoke(null);
                                AE2CraftingLens.LOGGER.info("Found keyHelper: {}", keyHelper);
                                Method ofItemMethod = keyHelper.getClass().getMethod("of", net.minecraft.world.item.ItemStack.class);
                                aeKey = ofItemMethod.invoke(keyHelper, hoveredStack);
                                AE2CraftingLens.LOGGER.info("AEKey found via AEApi.key().of(): {}", aeKey);
                            } catch (Exception e) {
                                AE2CraftingLens.LOGGER.error("Error creating AEKey from ItemStack: {}", e.getMessage());
                            }
                        } else {
                            AE2CraftingLens.LOGGER.info("getItem() returned null");
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.error("Error calling getItem() method: {}", e.getMessage());
                    }
                }
                
                if (aeKey != null) {
                    AE2CraftingLens.LOGGER.info("Found AEKey: {}", aeKey);
                    try {
                        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                        if (aeKeyClass.isInstance(aeKey)) {
                            RequestPatternProvidersPacket packet = new RequestPatternProvidersPacket(aeKey);
                            AE2CraftingLens.LOGGER.info("Sending RequestPatternProvidersPacket for: {}", aeKey);
                            PacketDistributor.sendToServer(packet);
                            AE2CraftingLens.LOGGER.info("Packet sent successfully");
                        } else {
                            AE2CraftingLens.LOGGER.info("Object is not an instance of AEKey: {}", aeKey.getClass().getName());
                        }
                    } catch (ClassNotFoundException e) {
                        AE2CraftingLens.LOGGER.error("AEKey class not found", e);
                    }
                } else {
                    AE2CraftingLens.LOGGER.info("No AEKey found for hoveredStack: {}", hoveredStack);
                }
            } else {
                AE2CraftingLens.LOGGER.info("No hoveredStack found at mouse position: {}, {}", event.getMouseX(), event.getMouseY());
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling mouse click", e);
        }
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Mouse click event processed ===");
    }
}
