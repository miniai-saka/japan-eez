package io.github.miniai_saka.eez;

import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class SurveyMarkerBlock extends Block {
    public final String kind;
    private final VoxelShape shape;
    public SurveyMarkerBlock(String kind, double width) {
        super(Settings.create().strength(.7f).nonOpaque().sounds(net.minecraft.sound.BlockSoundGroup.METAL));
        this.kind=kind; double a=(16-width)/2; shape=Block.createCuboidShape(a,0,a,16-a,3.2,16-a);
    }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return shape; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return shape; }
    @Override public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) { return world.getBlockState(pos.down()).isSideSolidFullSquare(world,pos.down(),Direction.UP); }
    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction dir, BlockState other, WorldAccess world, BlockPos pos, BlockPos neighbor) {
        return dir==Direction.DOWN && !canPlaceAt(state,world,pos) ? Blocks.AIR.getDefaultState() : super.getStateForNeighborUpdate(state,dir,other,world,pos,neighbor);
    }
    @Override public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old, boolean notify) {
        if(world instanceof ServerWorld sw && !old.isOf(this)) { BoundaryState s=BoundaryState.get(sw); s.markers.put(pos.asLong(),new BoundaryState.Marker(pos.toImmutable(),kind,JapanEez.config.defaultOwner)); s.changed(); }
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if(world instanceof ServerWorld sw && placer instanceof ServerPlayerEntity p) {
            BoundaryState s=BoundaryState.get(sw); String active=JapanEez.activeRegions.get(p.getUuid()); BoundaryState.Region r=s.regions.get(active);
            s.markers.put(pos.asLong(),new BoundaryState.Marker(pos.toImmutable(),kind,r==null?JapanEez.config.defaultOwner:r.owner)); s.changed();
        }
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if(world instanceof ServerWorld sw && !next.isOf(this)) { BoundaryState s=BoundaryState.get(sw); s.markers.remove(pos.asLong()); s.changed(); }
        super.onStateReplaced(state,world,pos,next,moved);
    }
    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if(!player.getStackInHand(hand).isOf(JapanEez.TOOL)) return ActionResult.PASS;
        if(player instanceof ServerPlayerEntity p) {
            JapanEez.selectedMarkers.put(p.getUuid(),pos.toImmutable());
            if(p.isSneaking()) Commands.addSelected(p);
            else p.sendMessage(Text.literal("基準点を選択: "+pos.toShortString()+" / Shift+右クリックで選択領域に頂点を追加"),true);
        } return ActionResult.SUCCESS;
    }
    @Override public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tips, net.minecraft.client.item.TooltipContext context) {
        tips.add(Text.literal("中央設置 / 厚み 0.2ブロック").formatted(Formatting.GRAY));
        tips.add(Text.literal("境界確認ツールで右クリックして選択").formatted(Formatting.GRAY));
    }
    public static final class MarkerItem extends BlockItem {
        private final String kind;
        public MarkerItem(SurveyMarkerBlock block) { super(block,new Item.Settings()); kind=block.kind; }
        @Override public ActionResult useOnBlock(ItemUsageContext context) {
            if(!JapanEez.config.markerEnabled.getOrDefault(kind,true) || (JapanEez.config.placementRequiresOp && context.getPlayer()!=null && !context.getPlayer().hasPermissionLevel(2))) {
                if(context.getPlayer()!=null) context.getPlayer().sendMessage(Text.literal("この測量標の設置は許可されていません。"),true); return ActionResult.FAIL;
            }
            if(context.getWorld() instanceof ServerWorld sw && BoundaryState.get(sw).markers.size()>=JapanEez.config.maxMarkers) return ActionResult.FAIL;
            return super.useOnBlock(context);
        }
    }
}
