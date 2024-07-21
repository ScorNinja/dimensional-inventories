package net.thomilist.dimensionalinventories.module.builtin.inventory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.thomilist.dimensionalinventories.lostandfound.LostAndFound;
import net.thomilist.dimensionalinventories.util.gson.SerializerPair;

import java.lang.reflect.Type;

public class ItemStackSerializerPair
    implements SerializerPair<ItemStack>
{
    @Override
    public ItemStack fromJson(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException
    {
        NbtCompound nbt = context.deserialize(json, NbtCompound.class);

        if (nbt == null)
        {
            return null;
        }

        ItemStack itemStack = ItemStack.fromNbt(nbt);

        if (itemStack == null)
        {
            LostAndFound.log("Invalid NBT compound for item stack", nbt.toString());
            return null;
        }

        return itemStack;
    }

    @Override
    public JsonElement toJson(ItemStack src, Type typeOfSrc, JsonSerializationContext context)
    {
        return context.serialize(src.writeNbt(new NbtCompound()), NbtCompound.class);
    }
}
