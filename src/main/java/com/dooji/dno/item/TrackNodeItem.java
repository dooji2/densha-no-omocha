package com.dooji.dno.item;

import com.dooji.dno.registry.TrainModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class TrackNodeItem extends BlockItem {
    public TrackNodeItem(Settings settings) {
        super(TrainModBlocks.TRACK_NODE_BLOCK, settings);
    }

    @Override
    public ActionResult place(ItemPlacementContext context) {
        ActionResult result = super.place(context);

        if (result == ActionResult.SUCCESS) {
            Direction playerFacing = context.getHorizontalPlayerFacing();
            BlockPos pos = context.getBlockPos();
            World world = context.getWorld();

            BlockState currentState = world.getBlockState(pos);
            BlockState newState = currentState.with(Properties.HORIZONTAL_FACING, playerFacing);
            world.setBlockState(pos, newState);
        }

        return result;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.dno.track_node");
    }
}
