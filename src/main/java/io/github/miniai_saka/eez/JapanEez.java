package io.github.miniai_saka.eez;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.item.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.*;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.slf4j.*;
import java.util.*;

public final class JapanEez implements ModInitializer {
    public static final String ID="japan_eez";
    public static final Logger LOG=LoggerFactory.getLogger(ID);
    public static Config config=new Config();
    public static long revision;
    public static final Identifier SNAPSHOT=id("snapshot");
    public static final Map<UUID,String> activeRegions=new HashMap<>();
    public static final Map<UUID,BlockPos> selectedMarkers=new HashMap<>();
    public static final Map<String,SurveyMarkerBlock> MARKERS=new LinkedHashMap<>();
    public static final Item TOOL=new Item(new Item.Settings().maxCount(1));
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static final Map<ServerWorld,SeaSurvey> surveys=new HashMap<>();
    public static Identifier id(String path) { return new Identifier(ID,path); }
    public static final class Session {
        public ServerWorld world;
        public SeaSurvey.Job job;
        public SeaGrid grid;
        public String notification="",status="海岸線を調査中…";
        public long regionRevision=-1;
    }
    public record Span(Layer layer,double x1,double z1,double x2,double z2,double low,double high,boolean box) {}
    @Override public void onInitialize() {
        try { config=Config.load(); } catch(Exception e) { LOG.error("japan-eez.json is invalid; using safe defaults without overwriting it",e); }
        for(String kind:List.of("triangulation_point","benchmark","metal_tack","survey_nail","boundary_stake")) {
            double width=switch(kind) {case "triangulation_point","benchmark"->6;case "boundary_stake"->5;default->4;};
            SurveyMarkerBlock block=new SurveyMarkerBlock(kind,width);MARKERS.put(kind,block);
            Registry.register(Registries.BLOCK,id(kind),block);Registry.register(Registries.ITEM,id(kind),new SurveyMarkerBlock.MarkerItem(block));
        }
        Registry.register(Registries.ITEM,id("boundary_viewer"),TOOL);
        Registry.register(Registries.ITEM_GROUP,id("survey"),FabricItemGroup.builder().displayName(Text.literal("日本の境界・EEZ"))
            .icon(()->new ItemStack(TOOL)).entries((context,entries)->{ entries.add(TOOL);MARKERS.values().forEach(entries::add); }).build());
        Commands.register();
        ServerTickEvents.END_SERVER_TICK.register(JapanEez::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{ UUID id=handler.player.getUuid();sessions.remove(id);activeRegions.remove(id);selectedMarkers.remove(id); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{sessions.clear();surveys.clear();activeRegions.clear();selectedMarkers.clear();});
        LOG.info("Japan Boundaries 1.0.0 initialized (Fabric 1.20.1)");
    }
    public static boolean holding(ServerPlayerEntity p) { return p.getMainHandStack().isOf(TOOL)||p.getOffHandStack().isOf(TOOL); }
    public static void invalidate() { revision++;surveys.values().forEach(SeaSurvey::clear); }
    public static Session session(ServerPlayerEntity p) { return sessions.computeIfAbsent(p.getUuid(),id->new Session()); }
    private static void tick(MinecraftServer server) {
        List<ServerPlayerEntity> players=server.getPlayerManager().getPlayerList(); if(players.isEmpty())return;
        int budget=config.samplesPerTick;
        // Rotate the first player so one large survey cannot starve other clients.
        for(int a=0;a<players.size();a++) {
            ServerPlayerEntity p=players.get((a+server.getTicks())%players.size());
            if(!holding(p)&&!config.notifications) continue;
            Session s=session(p); ServerWorld w=p.getServerWorld();
            if(s.world!=w) { if(s.world!=null){activeRegions.remove(p.getUuid());selectedMarkers.remove(p.getUuid());} s.world=w;s.job=null;s.grid=null;s.notification=""; }
            if(s.job==null || s.job.revision!=revision || (s.job.result!=null && (Math.abs(p.getX()-s.job.cx)>32 || Math.abs(p.getZ()-s.job.cz)>32))) {
                if(s.job!=null && (s.job.revision!=revision || Math.abs(p.getX()-s.job.cx)>config.renderRadius || Math.abs(p.getZ()-s.job.cz)>config.renderRadius))s.grid=null;
                SeaSurvey survey=surveys.computeIfAbsent(w,key->new SeaSurvey());s.job=survey.new Job(p.getBlockX(),p.getBlockZ());
            }
            if(s.job.result==null && budget>0) { int slice=Math.min(budget,Math.max(128,config.samplesPerTick/players.size()));s.job.advance(w,slice);budget-=slice; }
            if(s.job.result!=null) s.grid=s.job.result;
            if(server.getTicks()%10==0) updateStatus(p,s);
            if(holding(p) && server.getTicks()%config.updateTicks==0 && ServerPlayNetworking.canSend(p,SNAPSHOT)) send(p,s);
        }
    }
    public static void updateStatus(ServerPlayerEntity p,Session s) {
        BoundaryState state=BoundaryState.get(p.getServerWorld()); BoundaryState.Region region=state.at(p.getX(),p.getZ());
        String kind="outside",owner=config.defaultOwner,message="境界外";double y=p.getY();
        int sea=p.getWorld().getSeaLevel();
        if(region!=null && y>=region.minY && (y<=region.maxY || (config.airspace&&y<=config.airspaceTopY))) {
            owner=region.owner;kind="territory:"+region.id;message=config.territoryMessage;
            int ground=p.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getBlockX(),p.getBlockZ());
            if(config.airspace && y>ground+2) {if(y<=config.airspaceTopY){kind="air:"+region.id;message=config.airspaceMessage;}else{kind="outside";message="境界外";}}
        } else if(s.grid!=null) {
            int i=s.grid.index(p.getX(),p.getZ());
            if(i>=0 && s.grid.biome[i]!=0) {
                int near=s.grid.nearest[i];if(near>=0) owner=state.ownerAt(s.grid.x0+(near%s.grid.n+.5)*s.grid.step,s.grid.z0+(near/s.grid.n+.5)*s.grid.step);
                double dist=s.grid.distanceAt(p.getX(),p.getZ());
                if(dist<=config.territorialDistance && y>=config.undergroundBottomY && y<=config.airspaceTopY) {
                    kind="territorial";message=config.territorialMessage;
                    if(y>sea+2) { if(config.airspace) {kind="airspace";message=config.airspaceMessage;} else {kind="outside";message="境界外";} }
                } else if(y>=config.undergroundBottomY && y<=sea+2 && dist<=config.eezDistance) {kind="eez";message=config.eezMessage;}
                else if(y>=config.undergroundBottomY && y<=sea+2 && config.extendedShelf && s.grid.biome[i]==1 && s.grid.connectedShelf[i] && dist>config.eezDistance && dist<=config.extendedDistance) {kind="shelf";message=config.shelfMessage;}
                s.status=Double.isFinite(dist)?String.format(java.util.Locale.ROOT,"海岸 %.1fブロック / %s / %s",dist,s.grid.biome[i]==2?"深い海":"通常の海",message.replace("{owner}",owner)):"周辺に海岸線なし / 境界外";
            } else s.status=region==null?"陸地 / 海域外":message.replace("{owner}",owner);
        } else s.status="海岸線を調査中… "+(s.job==null?0:s.job.progress())+"%";
        if(region!=null) s.status=message.replace("{owner}",owner);
        String key=kind+":"+owner;
        if(!key.equals(s.notification)) {
            s.notification=key;
            if(config.notifications && !kind.equals("outside")) {
                p.networkHandler.sendPacket(new TitleFadeS2CPacket(10,55,15));
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(message.replace("{owner}",owner))));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("日本の境界・EEZ")));
            }
        }
    }
    public static List<Span> spans(ServerPlayerEntity p,Session s) {
        Config c=config;List<Span> spans=new ArrayList<>();BoundaryState state=BoundaryState.get(p.getServerWorld());
        int radius=c.renderRadius+32;double sea=p.getWorld().getSeaLevel()+.03;
        for(BoundaryState.Marker m:state.markers.values()) if(Math.abs(m.pos().getX()-p.getX())<=radius && Math.abs(m.pos().getZ()-p.getZ())<=radius) {
            spans.add(new Span(Layer.MARKERS,m.pos().getX()+.35,m.pos().getZ()+.35,m.pos().getX()+.65,m.pos().getZ()+.65,c.undergroundBottomY,c.displayTopY,true));
            if(spans.size()>=1024)break;
        }
        for(BoundaryState.Region r:state.regions.values()) for(int i=0;i<r.vertices.size()-(r.closed?0:1);i++) {
            BlockPos a=r.vertices.get(i),b=r.vertices.get((i+1)%r.vertices.size());
            if(Geometry.segmentDistance(p.getX(),p.getZ(),a.getX()+.5,a.getZ()+.5,b.getX()+.5,b.getZ()+.5)>radius)continue;
            spans.add(new Span(Layer.TERRITORY,a.getX()+.5,a.getZ()+.5,b.getX()+.5,b.getZ()+.5,r.minY,Math.min(r.maxY,c.displayTopY),false));
            if(r.baseline) spans.add(new Span(Layer.BASELINE,a.getX()+.5,a.getZ()+.5,b.getX()+.5,b.getZ()+.5,Math.min(a.getY(),b.getY())+.2,Math.max(a.getY(),b.getY())+.2,false));
            if(c.airspace && r.closed) spans.add(new Span(Layer.AIRSPACE,a.getX()+.5,a.getZ()+.5,b.getX()+.5,b.getZ()+.5,sea,c.airspaceTopY,false));
            if(spans.size()>=2048)break;
        }
        if(s.grid!=null) {
            SeaGrid g=s.grid;int min=Math.max(1,(g.n-1)/2-(c.renderRadius+16+g.step-1)/g.step+4),max=g.n-min-1;
            for(Layer layer:List.of(Layer.BASELINE,Layer.TERRITORIAL,Layer.EEZ,Layer.SHELF,Layer.AIRSPACE)) {
                if(layer==Layer.AIRSPACE && !c.airspace) continue;
                double lo=layer==Layer.BASELINE?sea:layer==Layer.AIRSPACE?sea:c.undergroundBottomY;
                double hi=layer==Layer.BASELINE?sea:layer==Layer.AIRSPACE?c.airspaceTopY:Math.min(c.displayTopY,sea);
                for(Geometry.Edge e:Geometry.outline(g.mask(layer,c),g.n,g.x0,g.z0,g.step,min,max)) {
                    spans.add(new Span(layer,e.x1(),e.z1(),e.x2(),e.z2(),lo,hi,false));if(spans.size()>=8192)return spans;
                }
            }
        } return spans;
    }
    public static void send(ServerPlayerEntity p,Session s) {
        PacketByteBuf b=PacketByteBufs.create();b.writeString(p.getWorld().getRegistryKey().getValue().toString());
        b.writeString(s.status,1024);b.writeInt(s.job==null?0:s.job.progress());b.writeInt(config.undergroundBottomY);b.writeInt(config.displayTopY);b.writeInt(config.airspaceTopY);b.writeBoolean(config.showThroughBlocks);
        b.writeInt(config.territorialDistance);b.writeInt(config.eezDistance);b.writeInt(config.extendedDistance);b.writeBoolean(config.extendedShelf);
        for(Layer l:Layer.values())b.writeInt(config.colors.get(l.key));
        List<Span> spans=spans(p,s);b.writeInt(spans.size());
        for(Span t:spans) { b.writeByte(t.layer.ordinal());b.writeDouble(t.x1);b.writeDouble(t.z1);b.writeDouble(t.x2);b.writeDouble(t.z2);b.writeDouble(t.low);b.writeDouble(t.high);b.writeBoolean(t.box); }
        ServerPlayNetworking.send(p,SNAPSHOT,b);
    }
}
