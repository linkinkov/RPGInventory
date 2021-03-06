/*
 * This file is part of RPGInventory.
 * Copyright (C) 2015-2017 Osip Fatkullin
 *
 * RPGInventory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RPGInventory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RPGInventory.  If not, see <http://www.gnu.org/licenses/>.
 */

package ru.endlesscode.rpginventory.item;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.endlesscode.rpginventory.RPGInventory;
import ru.endlesscode.rpginventory.api.InventoryAPI;
import ru.endlesscode.rpginventory.event.listener.ItemListener;
import ru.endlesscode.rpginventory.inventory.InventoryManager;
import ru.endlesscode.rpginventory.misc.Config;
import ru.endlesscode.rpginventory.misc.FileLanguage;
import ru.endlesscode.rpginventory.pet.PetManager;
import ru.endlesscode.rpginventory.pet.PetType;
import ru.endlesscode.rpginventory.utils.ItemUtils;
import ru.endlesscode.rpginventory.utils.PlayerUtils;
import ru.endlesscode.rpginventory.utils.StringUtils;

import java.io.File;
import java.util.*;

/**
 * Created by OsipXD on 18.09.2015
 * It is part of the RpgInventory.
 * All rights reserved 2014 - 2016 © «EndlessCode Group»
 */
public class ItemManager {
    private static final Map<String, CustomItem> CUSTOM_ITEMS = new HashMap<>();
    private static final List<String> LORE_PATTERN = Config.getConfig().getStringList("items.lore-pattern");
    private static final String LORE_SEPARATOR = Config.getConfig().getString("items.separator");

    private ItemManager() {
    }

    public static boolean init(RPGInventory instance) {
        try {
            File itemsFile = new File(RPGInventory.getInstance().getDataFolder(), "items.yml");
            if (!itemsFile.exists()) {
                RPGInventory.getInstance().saveResource("items.yml", false);
            }

            FileConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);

            CUSTOM_ITEMS.clear();
            for (String key : itemsConfig.getConfigurationSection("items").getKeys(false)) {
                CustomItem customItem = new CustomItem(key, itemsConfig.getConfigurationSection("items." + key));
                CUSTOM_ITEMS.put(key, customItem);
            }

            RPGInventory.getPluginLogger().info(CUSTOM_ITEMS.size() + " item(s) has been loaded");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (CUSTOM_ITEMS.size() == 0) {
            return false;
        }

        instance.getServer().getPluginManager().registerEvents(new ItemListener(), instance);
        return true;
    }

    public static Modifier getModifier(@NotNull Player player, ItemStat.StatType statType) {
        return getModifier(player, statType, false);
    }

    private static Modifier getModifier(@NotNull Player player, ItemStat.StatType statType, boolean notifyPlayer) {
        double minBonus = 0;
        double maxBonus = 0;
        float minMultiplier = 1;
        float maxMultiplier = 1;

        List<ItemStack> items = new ArrayList<>(InventoryAPI.getPassiveItems(player));
        Collections.addAll(items, player.getInventory().getArmorContents());

        ItemStack itemInHand = player.getEquipment().getItemInMainHand();
        if (CustomItem.isCustomItem(itemInHand) && ItemManager.allowedForPlayer(player, itemInHand, notifyPlayer)) {
            items.add(itemInHand);
        }

        itemInHand = player.getEquipment().getItemInOffHand();
        if (CustomItem.isCustomItem(itemInHand) && ItemManager.allowedForPlayer(player, itemInHand, notifyPlayer)) {
            items.add(itemInHand);
        }

        for (ItemStack item : items) {
            CustomItem customItem;
            ItemStat stat;
            if (!CustomItem.isCustomItem(item) || (customItem = ItemManager.getCustomItem(item)) == null
                    || (stat = customItem.getStat(statType)) == null) {
                continue;
            }

            if (stat.isPercentage()) {
                minMultiplier += stat.getOperationType() == ItemStat.OperationType.MINUS ? -stat.getMinValue()/100 : stat.getMinValue()/100;

                if (stat.isRanged()) {
                    maxMultiplier += stat.getOperationType() == ItemStat.OperationType.MINUS ? -stat.getMaxValue()/100 : stat.getMaxValue()/100;
                } else {
                    maxMultiplier += stat.getOperationType() == ItemStat.OperationType.MINUS ? -stat.getMinValue()/100 : stat.getMinValue()/100;
                }
            } else {
                minBonus += stat.getOperationType() == ItemStat.OperationType.MINUS ? -stat.getMinValue() : stat.getMinValue();

                if (stat.isRanged()) {
                    maxBonus += stat.getOperationType() == ItemStat.OperationType.MINUS ? -stat.getMaxValue() : stat.getMaxValue();
                } else {
                    maxBonus += stat.getOperationType() == ItemStat.OperationType.MINUS ? -stat.getMinValue() : stat.getMinValue();
                }
            }
        }

        return new Modifier(minBonus, maxBonus, minMultiplier, maxMultiplier);
    }

    public static List<String> getItemList() {
        List<String> itemList = new ArrayList<>();
        itemList.addAll(CUSTOM_ITEMS.keySet());
        return itemList;
    }

    public static ItemStack getItem(String itemId) {
        CustomItem customItem = CUSTOM_ITEMS.get(itemId);
        return customItem == null ? new ItemStack(Material.AIR) : customItem.getItemStack();
    }

    @Nullable
    public static CustomItem getCustomItem(@NotNull ItemStack item) {
        return CUSTOM_ITEMS.get(ItemUtils.getTag(item, ItemUtils.ITEM_TAG));
    }

    public static boolean allowedForPlayer(@NotNull Player player, @NotNull ItemStack item, boolean notifyPlayer) {
        ClassedItem classedItem;
        if (CustomItem.isCustomItem(item)) {
            classedItem = ItemManager.getCustomItem(item);
        } else if (PetType.isPetItem(item)) {
            classedItem = PetManager.getPetFromItem(item);
        } else {
            return true;
        }

        if (!PlayerUtils.checkLevel(player, classedItem.getLevel())) {
            if (notifyPlayer) {
                PlayerUtils.sendMessage(player, RPGInventory.getLanguage().getCaption("error.item.level", classedItem.getLevel()));
            }

            return false;
        }

        if (classedItem.getClasses() == null || PlayerUtils.checkClass(player, classedItem.getClasses())) {
            return true;
        }

        if (notifyPlayer) {
            PlayerUtils.sendMessage(player, RPGInventory.getLanguage().getCaption("error.item.class", classedItem.getClassesString()));
        }

        return false;
    }

    public static void updateStats(@NotNull final Player player) {
        if (!InventoryManager.playerIsLoaded(player)) {
            return;
        }

        InventoryManager.get(player).updateStatsLater();
    }

    static List<String> buildLore(CustomItem item) {
        FileLanguage lang = RPGInventory.getLanguage();
        List<String> lore = new ArrayList<>();
        boolean lastIsSeparator = false;
        for (String loreElement : LORE_PATTERN) {
            switch (loreElement) {
                case "_UNBREAKABLE_":
                    if (item.isUnbreakable()) {
                        lore.add(lang.getCaption("item.unbreakable"));
                        lastIsSeparator = false;
                    }
                    break;
                case "_DROP_":
                    if (!item.isDrop()) {
                        lore.add(lang.getCaption("item.nodrop"));
                        lastIsSeparator = false;
                    }
                    break;
                case "_SEPARATOR_":
                    if (!lastIsSeparator) {
                        lore.add(LORE_SEPARATOR);
                        lastIsSeparator = true;
                    }
                    break;
                case "_LEVEL_":
                    if (item.getLevel() != -1) {
                        lore.add(lang.getCaption("item.level", item.getLevel()));
                        lastIsSeparator = false;
                    }
                    break;
                case "_CLASS_":
                    if (item.getClasses() != null) {
                        lore.add(lang.getCaption("item.class", item.getClassesString()));
                        lastIsSeparator = false;
                    }
                    break;
                case "_LORE_":
                    if (item.getLore() != null) {
                        lore.addAll(item.getLore());
                        lastIsSeparator = false;
                    }
                    break;
                case "_SKILLS_":
                    if (item.hasLeftClickCaption()) {
                        lore.add(lang.getCaption("item.left-click", item.getLeftClickCaption()));
                        lastIsSeparator = false;
                    }
                    if (item.hasRightClickCaption()) {
                        lore.add(lang.getCaption("item.right-click", item.getRightClickCaption()));
                        lastIsSeparator = false;
                    }
                    break;
                case "_STATS_":
                    if (item.isStatsHidden()) {
                        lore.add(lang.getCaption("item.hide"));
                        lastIsSeparator = false;
                    } else {
                        for (ItemStat stat : item.getStats()) {
                            lore.add(lang.getCaption("stat." + stat.getType().name().toLowerCase(), stat.getStringValue()));
                            lastIsSeparator = false;
                        }
                    }
                    break;
                default:
                    lore.add(StringUtils.coloredLine(loreElement));
            }
        }

        if (lastIsSeparator) {
            lore.remove(lore.size() - 1);
        }

        if (lore.get(0).equals(LORE_SEPARATOR)) {
            lore.remove(0);
        }

        return lore;
    }
}
