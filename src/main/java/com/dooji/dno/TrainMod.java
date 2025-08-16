package com.dooji.dno;

import com.dooji.dno.network.TrainModNetworking;
import com.dooji.dno.registry.TrainModBlockEntities;
import com.dooji.dno.registry.TrainModBlocks;
import com.dooji.dno.registry.TrainModItemGroups;
import com.dooji.dno.registry.TrainModItems;
import com.dooji.dno.track.TrackManager;
import com.dooji.dno.train.TrainManager;
import com.dooji.dno.track.TrackPersistenceHandler;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrainMod implements ModInitializer {
	public static final String MOD_ID = "densha-no-omocha";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TrainModBlocks.initialize();
		TrainModBlockEntities.initialize();
		TrainModItems.initialize();
		TrainModItemGroups.initialize();
		TrainModNetworking.initialize();

		ServerWorldEvents.LOAD.register((server, world) -> {
			TrackManager.loadTracks(world);
			TrainManager.loadTrains(world);

			TrainModNetworking.broadcastTrainSync(world);
		});

		ServerWorldEvents.UNLOAD.register((server, world) -> {
			if (world != null) {
				TrackPersistenceHandler.saveTracks(world, TrackManager.getTracksFor(world));
				TrainManager.saveTrains(world);
				TrackManager.clearWorldData(world);
			}
		});
	}
}
