package io.github.miniai_saka.eez.qa;

import io.github.miniai_saka.eez.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

public final class ServerQa implements ModInitializer {
    private int x,z,y;
    private int activeTicks;
    private final Set<UUID> joined=new HashSet<>();
    private static void check(boolean pass,String name){if(!pass)throw new AssertionError("EEZ_QA FAIL "+name);JapanEez.LOG.info("EEZ_QA PASS {}",name);}
    private static int command(MinecraftServer server,String cmd){return server.getCommandManager().executeWithPrefix(server.getCommandSource(),cmd);}
    @Override public void onInitialize(){
        if(!Boolean.getBoolean("eez.qa"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var w=server.getOverworld();var state=BoundaryState.get(w);JapanEez.LOG.info("EEZ_QA existing markers={} regions={}",state.markers.size(),state.regions.size());
            SeaSurvey survey=new SeaSurvey();boolean found=false;
            for(int r=0;r<=2048&&!found;r+=32)for(int a=-r;a<=r&&!found;a+=32)for(int b=-r;b<=r;b+=32){if(Math.abs(a)!=r&&Math.abs(b)!=r)continue;if(survey.sample(w,a,b)==1&&(survey.sample(w,a-32,b)==0||survey.sample(w,a+32,b)==0||survey.sample(w,a,b-32)==0||survey.sample(w,a,b+32)==0)){x=a;z=b;found=true;break;}}
            y=w.getSeaLevel()+2;w.setSpawnPos(new BlockPos(x+12,y+1,z+20),180);
            for(int a=0;a<24;a++)for(int b=0;b<24;b++){w.setBlockState(new BlockPos(x+a,y-1,z+b),Blocks.SMOOTH_QUARTZ.getDefaultState());for(int h=0;h<8;h++)w.setBlockState(new BlockPos(x+a,y+h,z+b),Blocks.AIR.getDefaultState());}
            int i=0;for(var block:JapanEez.MARKERS.values()){
                BlockPos p=new BlockPos(x+4+3*i++,y,z+8);w.setBlockState(p,block.getDefaultState());var shape=block.getDefaultState().getOutlineShape(w,p).getBoundingBox();
                check(Math.abs(shape.maxY-.2)<1e-8,"marker height .2 "+block.kind);check(Math.abs((shape.minX+shape.maxX)/2-.5)<1e-8&&Math.abs((shape.minZ+shape.maxZ)/2-.5)<1e-8,"marker centered "+block.kind);
            }
            state.regions.remove("qa");command(server,"jpboundary region create qa A");
            for(int[] a:new int[][]{{2,2},{21,2},{21,21},{2,21}})command(server,"jpboundary region vertex qa "+(x+a[0])+" "+y+" "+(z+a[1]));
            check(command(server,"jpboundary region close qa")==1,"region closes");check(state.regions.get("qa").contains(x+12,z+12),"region containment");
            check(command(server,"jpboundary region baseline qa true")==1&&state.regions.get("qa").baseline,"survey baseline switch");
            check(command(server,"jpboundary region export")==1&&command(server,"jpboundary region import")==1,"region JSON roundtrip");
            var restored=BoundaryState.read(state.writeNbt(new net.minecraft.nbt.NbtCompound()));check(restored.markers.size()==state.markers.size()&&restored.regions.get("qa").closed,"NBT persistence roundtrip");
            check(command(server,"jpboundary config set extendedShelf false")==1&&!JapanEez.config.extendedShelf,"extended switch off");command(server,"jpboundary config set extendedShelf true");
            int old=JapanEez.config.eezDistance;command(server,"jpboundary config set eezDistance 5");check(JapanEez.config.eezDistance==old,"invalid configuration rollback");
            check(command(server,"jpboundary config set airspaceTopY 256")==1&&JapanEez.config.airspaceTopY==256,"airspace command");command(server,"jpboundary config set airspaceTopY 320");
            JapanEez.LOG.info("EEZ_QA SERVER_READY coast={},{} platformY={} coastFound={}",x,z,y,found);
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!server.getPlayerManager().getPlayerList().isEmpty()) { activeTicks++;if(activeTicks==200){command(server,"jpboundary config set airspaceTopY 192");command(server,"jpboundary config set extendedShelf false");}if(activeTicks==360){command(server,"jpboundary config set airspaceTopY 320");command(server,"jpboundary config set extendedShelf true");} }
            for(var p:server.getPlayerManager().getPlayerList())if(joined.add(p.getUuid())){
            server.getPlayerManager().addToOperators(p.getGameProfile());p.changeGameMode(GameMode.CREATIVE);
            p.teleport(server.getOverworld(),x+12.5,y+2,z+19.5,180,30);
            var block=JapanEez.MARKERS.get("survey_nail");p.getInventory().selectedSlot=0;p.getInventory().setStack(0,new ItemStack(block));
            BlockPos support=new BlockPos(x+8,y-1,z+13);var hit=new BlockHitResult(Vec3d.ofCenter(support).add(0,.5,0),Direction.UP,support,false);
            check(block.asItem().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,hit)).isAccepted(),"physical marker placement");
            BlockPos nail=support.up();check(BoundaryState.get(p.getServerWorld()).markers.containsKey(nail.asLong()),"placed marker registered");p.getServerWorld().breakBlock(support,false);check(!BoundaryState.get(p.getServerWorld()).markers.containsKey(nail.asLong()),"support removal cleans marker");p.getServerWorld().setBlockState(support,Blocks.SMOOTH_QUARTZ.getDefaultState());
            command(server,"jpboundary config set airspaceTopY 512");check(JapanEez.spans(p,JapanEez.session(p)).stream().anyMatch(t->t.layer()==Layer.AIRSPACE&&t.high()==512),"airspace render rises above region height");command(server,"jpboundary config set airspaceTopY 320");classify(p);
            p.teleport(server.getOverworld(),x+12.5,y+2,z+19.5,180,30);p.getInventory().setStack(0,new ItemStack(JapanEez.TOOL));int slot=1;for(var b:JapanEez.MARKERS.values())p.getInventory().setStack(slot++,new ItemStack(b,64));
            p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();JapanEez.LOG.info("EEZ_QA PLAYER_READY {}",p.getGameProfile().getName());
        }});
    }
    private void classify(ServerPlayerEntity p){
        int n=380;byte[] b=new byte[n*n];Arrays.fill(b,(byte)1);for(int a=0;a<n;a++)for(int i=0;i<10;i++)b[a*n+i]=0;
        SeaGrid g=new SeaGrid(n,1,x+1000,z+1000,b);var s=new JapanEez.Session();s.world=p.getServerWorld();s.grid=g;
        int sea=p.getWorld().getSeaLevel();
        for(int[] test:new int[][]{{21,0},{22,1},{209,1},{210,2},{359,2},{360,3}}){p.teleport(p.getServerWorld(),g.x0+test[0]+.5,sea,g.z0+190.5,0,0);JapanEez.updateStatus(p,s);String expected=switch(test[1]){case 0->"territorial:";case 1->"eez:";case 2->"shelf:";default->"outside:";};check(s.notification.startsWith(expected),"entry title at "+g.distance[190*n+test[0]]+" = "+expected);}
        p.teleport(p.getServerWorld(),g.x0+21.5,JapanEez.config.airspaceTopY+1,g.z0+190.5,0,0);JapanEez.updateStatus(p,s);check(s.notification.startsWith("outside:"),"airspace upper bound");
    }
}
