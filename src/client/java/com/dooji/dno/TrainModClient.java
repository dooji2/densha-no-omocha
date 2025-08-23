package com.dooji.dno;

import com.dooji.dno.block.TrackNodeBlockRenderer;
import com.dooji.dno.network.TrainModClientNetworking;
import com.dooji.dno.registry.TrainModItems;
import com.dooji.dno.track.TrackItemClientHandler;
import com.dooji.dno.track.TrackRenderer;
import com.dooji.dno.track.WrenchHoverRenderer;
import com.dooji.dno.train.TrainBoardingManager;
import com.dooji.dno.train.TrainBoardingRenderer;
import com.dooji.dno.train.TrainConfigLoader;
import com.dooji.dno.train.TrainRenderer;
import com.dooji.renderix.Renderix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dooji.dno.track.TrackManagerClient;
import com.dooji.dno.track.RouteManagerClient;
import com.dooji.dno.track.TrackConfigLoader;
import com.dooji.dno.train.TrainManagerClient;

public class TrainModClient implements ClientModInitializer {
	public static final String MOD_ID = "densha-no-omocha-client";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyBinding disembarkKey;

	@Override
	public void onInitializeClient() {
		disembarkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.dno.disembark",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_R,
			"category.dno.general"
		));

		TrainModClientNetworking.init();
		TrackItemClientHandler.init();
		TrackRenderer.init();
		WrenchHoverRenderer.init();
		TrackNodeBlockRenderer.init();
		TrainRenderer.init();
		TrainConfigLoader.init();
		TrainBoardingManager.init();
		TrainBoardingRenderer.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (disembarkKey.wasPressed() && TrainBoardingManager.isPlayerBoarded()) {
				TrainBoardingManager.requestDisembark();
			}
		});

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TrainMod.MOD_ID, "track_types");
			}

			@Override
			public void reload(ResourceManager manager) {
				TrackRenderer.clearCaches();
				TrackConfigLoader.loadTrackTypes(manager);
			}
		});

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TrainMod.MOD_ID, "renderix_models");
			}

			@Override
			public void reload(ResourceManager manager) {
				Renderix.clearCache();
			}
		});

		ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipType, list) -> {
			if (itemStack.isOf(TrainModItems.TRACK_ITEM)) {
				list.add(Text.translatable("item.dno.track_item.tooltip").formatted(Formatting.GRAY));
			} else if (itemStack.isOf(TrainModItems.TRACK_NODE)) {
				list.add(Text.translatable("item.dno.track_node.tooltip").formatted(Formatting.GRAY));
			} else if (itemStack.isOf(TrainModItems.CROWBAR)) {
				list.add(Text.translatable("item.dno.crowbar.tooltip").formatted(Formatting.GRAY));
			}
		});

		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
			TrackManagerClient.removeAll();
			TrainManagerClient.clearAllTrains();
			RouteManagerClient.clearAllRoutes();
			TrainBoardingRenderer.clearCaches();
			TrainBoardingManager.resetBoardingState();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TrainBoardingManager.resetBoardingState();
		});
	}
}
