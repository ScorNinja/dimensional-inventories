package net.thomilist.dimensionalinventories;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DimensionalInventoriesMod implements ModInitializer
{
	public static final Logger LOGGER = LoggerFactory.getLogger("DimensionalInventories");

	@Override
	public void onInitialize()
	{
		ServerLifecycleEvents.SERVER_STARTING.register((server) ->
		{
			InventoryManager.onServerStart(server);
			DimensionPoolManager.onServerStart(server);
			LOGGER.info("Dimensional Inventories initialised.");
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DimensionalInventoriesCommands.register(dispatcher));

		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
		{
			String originDimensionName = origin.getRegistryKey().getValue().toString();
			String destinationDimensionName = destination.getRegistryKey().getValue().toString();
			
			handlePlayerDimensionChange(player, originDimensionName, destinationDimensionName);

			return;
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
		{
			String originDimensionName = oldPlayer.getWorld().getRegistryKey().getValue().toString();
			String destinationDimensionName = newPlayer.getWorld().getRegistryKey().getValue().toString();

			handlePlayerDimensionChange(newPlayer, originDimensionName, destinationDimensionName);

			return;
		});

		ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((originalEntity, newEntity, origin, destination) ->
		{
			String originDimensionName = origin.getRegistryKey().getValue().toString();
			String destinationDimensionName = destination.getRegistryKey().getValue().toString();

			handleEntityDimensionChange(newEntity, originDimensionName, destinationDimensionName);

			return;
		});
	}

	public static void handlePlayerDimensionChange(ServerPlayerEntity player, String originDimensionName, String destinationDimensionName)
	{
		LOGGER.debug("Player '" + player.getName().getString() + "' travelled from " + originDimensionName + " to " + destinationDimensionName + ".");

		if (DimensionPoolManager.samePoolContainsBoth(originDimensionName, destinationDimensionName))
		{
			LOGGER.debug("The origin and destination dimensions are in the same pool. Player unaffected.");
		}
		else
		{
			LOGGER.debug("The origin and destination dimensions are in different pools. Switching inventories...");

			Optional<DimensionPool> originDimension = DimensionPoolManager.getPoolWithDimension(originDimensionName);
			Optional<DimensionPool> destinationDimension = DimensionPoolManager.getPoolWithDimension(destinationDimensionName);

			if (originDimension.isEmpty() || destinationDimension.isEmpty())
			{
				LOGGER.warn("Not all dimensions are assigned a dimension pool. Player '" + player.getName().getString() + "' unaffected (" + originDimensionName + " -> " + destinationDimensionName + ").");
				return;
			}

			String originDimensionPoolName = originDimension.get().getName();
			String destinationDimensionPoolName = destinationDimension.get().getName();

			InventoryManager.saveInventory(player, originDimensionPoolName);
			InventoryManager.clearInventory(player);
			InventoryManager.loadInventory(player, destinationDimensionPoolName);

			Optional<GameMode> gameMode = DimensionPoolManager.getGameModeOfDimensionPool(destinationDimensionPoolName);
			if (gameMode.isPresent())
			{
				player.changeGameMode(gameMode.get());
			}

		}

		return;
	}

	public static void handleEntityDimensionChange(Entity newEntity, String originDimensionName, String destinationDimensionName)
	{
		if (!DimensionPoolManager.samePoolContainsBoth(originDimensionName, destinationDimensionName))
		{
			LOGGER.debug("Entity '" + newEntity.getName().getString() + "' travelled from " + originDimensionName + " to " + destinationDimensionName + ".");
			LOGGER.debug("The origin and destination dimensions are in different pools. Deleting entity...");

			Optional<DimensionPool> originDimension = DimensionPoolManager.getPoolWithDimension(originDimensionName);
			Optional<DimensionPool> destinationDimension = DimensionPoolManager.getPoolWithDimension(destinationDimensionName);

			if (originDimension.isEmpty() || destinationDimension.isEmpty())
			{
				LOGGER.warn("Not all dimensions are assigned a dimension pool. Entity '" + newEntity.getName().getString() + "' unaffected (" + originDimensionName + " -> " + destinationDimensionName + ").");
				return;
			}

			newEntity.discard();
		}

		return;
	}
}
