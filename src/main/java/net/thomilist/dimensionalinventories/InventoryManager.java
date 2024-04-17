package net.thomilist.dimensionalinventories;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class InventoryManager
{
    private static Path saveDirectory;
    private static final int expectedInventoryDataLines = 73;

    public static void onServerStart(MinecraftServer server)
    {
        InventoryManager.saveDirectory = server.getSavePath(WorldSavePath.ROOT).resolve("dimensionalinventories");
        try
        {
            Files.createDirectories(saveDirectory);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return;
    }

    public static void clearInventory(ServerPlayerEntity player)
    {
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        ExperienceHelper.setExperience(player, 0);
        player.setScore(0);
        player.clearStatusEffects();
        return;
    }
    
    public static void saveInventory(ServerPlayerEntity player, String dimensionPool)
    {
        String nbtString = getNbtStringOfInventory(player);
        Path saveFile = getDimensionPoolPlayerPath(player, dimensionPool);

        if (saveFile == null)
        {
            return;
        }
        
        try
        {
            Files.writeString(saveFile, nbtString);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            DimensionalInventoriesMod.LOGGER.error("Error while writing inventory data.", e);
            return;
        }

        return;
    }

    public static void loadInventory(ServerPlayerEntity player, String dimensionPool)
    {
        String nbtString;
        Path saveFile = getDimensionPoolPlayerPath(player, dimensionPool);

        if (saveFile == null)
        {
            return;
        }

        try
        {
            nbtString = Files.readString(saveFile);
        }
        catch (FileNotFoundException e)
        {
            DimensionalInventoriesMod.LOGGER.warn("Inventory data file not found. It will be created when the player leaves the dimension.");
            return;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            DimensionalInventoriesMod.LOGGER.error("Error while reading inventory data.", e);
            return;
        }

        String[] nbtArray = nbtString.split("\\n");

        PlayerInventory inventory = player.getInventory();
        EnderChestInventory enderChest = player.getEnderChestInventory();
        int index = 0;

        try
        {
            for (int i = 0; i < inventory.armor.size(); i++)
            {
                NbtCompound nbt = getNbtFromString(nbtArray[index++]);

                if (nbt == null)
                {
                    nbt = lostItem(player, nbtArray[index - 1], "armor["+i+"]");
                }

                inventory.armor.set(i, ItemStack.fromNbt(nbt));
            }

            for (int i = 0; i < inventory.main.size(); i++)
            {
                NbtCompound nbt = getNbtFromString(nbtArray[index++]);

                if (nbt == null)
                {
                    nbt = lostItem(player, nbtArray[index - 1], "inventory["+i+"]");
                }

                inventory.main.set(i, ItemStack.fromNbt(nbt));
            }

            for (int i = 0; i < inventory.offHand.size(); i++)
            {
                NbtCompound nbt = getNbtFromString(nbtArray[index++]);

                if (nbt == null)
                {
                    nbt = lostItem(player, nbtArray[index - 1], "offhand");
                }

                inventory.offHand.set(i, ItemStack.fromNbt(nbt));
            }

            for (int i = 0; i < enderChest.heldStacks.size(); i++)
            {
                NbtCompound nbt = getNbtFromString(nbtArray[index++]);

                if (nbt == null)
                {
                    nbt = lostItem(player, nbtArray[index - 1], "enderchest["+i+"]");
                }

                enderChest.heldStacks.set(i, ItemStack.fromNbt(nbt));
            }

            int experiencePoints = Integer.parseInt(nbtArray[index++]);
            ExperienceHelper.setExperience(player, experiencePoints);

            int score = Integer.parseInt(nbtArray[index++]);
            player.setScore(score);

            player.getHungerManager().setFoodLevel(Integer.parseInt(nbtArray[index++]));
            player.getHungerManager().setSaturationLevel(Float.parseFloat(nbtArray[index++]));
            player.getHungerManager().setExhaustion(Float.parseFloat(nbtArray[index++]));

            player.setHealth(Float.parseFloat(nbtArray[index++]));
        }
        catch (IndexOutOfBoundsException e)
        {
            DimensionalInventoriesMod.LOGGER.warn("Expected " + expectedInventoryDataLines + " lines of inventory data, but found only " + nbtArray.length + ".");
        }

        return;
    }

    public static String lostItem(ServerPlayerEntity player, String nbtString, String location)
    {
        Path saveFile = getLostAndFoundPath();
        String replacedWith = getNbtFromString('{Count:1b,id:"minecraft:paper",tag:{Enchantements:[{}],display:{Name:'"Corrupted Item - Ask Tech about Lost and Found."'}}}');

        if (saveFile == null)
        {
            return replacedWith;
        }
        
        try
        {
            Files.writeString(saveFile, player.getUuidAsString() + " : " + location + " : " + nbtString + "\n");
            DimensionalInventoriesMod.LOGGER.info("Item sent to lost and found.");
        }
        catch (IOException e)
        {
            e.printStackTrace();
            DimensionalInventoriesMod.LOGGER.error("Error while saving lost and found data.", e);
            return replacedWith;
        }

        return replacedWith;
    }

    public static String getNbtStringOfInventory(ServerPlayerEntity player)
    {
        PlayerInventory inventory = player.getInventory();
        EnderChestInventory enderChest = player.getEnderChestInventory();

        StringBuilder nbtString = new StringBuilder();

        for (ItemStack armorPiece : inventory.armor)
        {
            nbtString.append(getNbtStringOfItemStack(armorPiece)).append("\n");
        }

        for (ItemStack inventorySlot : inventory.main)
        {
            nbtString.append(getNbtStringOfItemStack(inventorySlot)).append("\n");
        }

        for (ItemStack offHand : inventory.offHand)
        {
            nbtString.append(getNbtStringOfItemStack(offHand)).append("\n");
        }

        for (ItemStack enderChestSlot : enderChest.heldStacks)
        {
            nbtString.append(getNbtStringOfItemStack(enderChestSlot)).append("\n");
        }

        nbtString.append(ExperienceHelper.getTotalExperience_Meridanus(player.experienceLevel, player.getNextLevelExperience(), player.experienceProgress)).append("\n");
        nbtString.append(player.getScore()).append("\n");

        nbtString.append(player.getHungerManager().getFoodLevel()).append("\n");
        nbtString.append(player.getHungerManager().getSaturationLevel()).append("\n");
        nbtString.append(player.getHungerManager().getExhaustion()).append("\n");
        
        nbtString.append(player.getHealth()).append("\n");

        return nbtString.toString();
    }

    public static String getNbtStringOfItemStack(ItemStack itemStack)
    {
        NbtCompound nbt = new NbtCompound();
        nbt = itemStack.writeNbt(nbt);

        if (nbt == null)
        {
            return "";
        }

        String nbtString = nbt.toString();
        nbtString = nbtString.replace(" : ", ": ");

        DimensionalInventoriesMod.LOGGER.debug("NBT: " + nbtString);

        return nbtString;
    }

    public static Path getDimensionPoolPlayerPath(ServerPlayerEntity player, String dimensionPool)
    {
        Path directory = saveDirectory.resolve(dimensionPool);
        Path file = directory.resolve(player.getUuidAsString() + ".txt");

        try
        {
            Files.createDirectories(directory);

            if (!Files.exists(file))
            {
                Files.createFile(file);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            DimensionalInventoriesMod.LOGGER.error("File path error.", e);
            return null;
        }

        return file;
    }

    public static Path getLostAndFoundPath()
    {
        Path file = saveDirectory.resolve("lost-and-found.txt");

        try
        {
            Files.createDirectories(directory);

            if (!Files.exists(file))
            {
                Files.createFile(file);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            DimensionalInventoriesMod.LOGGER.error("File path error.", e);
            return null;
        }

        return file;
    }

    public static NbtCompound getNbtFromString(String nbtString)
    {
        NbtCompound nbt;
            
        try
        {
            nbt = NbtHelper.fromNbtProviderString(nbtString);
        }
        catch (CommandSyntaxException e)
        {
            e.printStackTrace();
            DimensionalInventoriesMod.LOGGER.error("Syntax error in inventory data", e);
            return null;
        }

        return nbt;
    }
}
