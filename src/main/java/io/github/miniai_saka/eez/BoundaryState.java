package io.github.miniai_saka.eez;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import java.util.*;

public final class BoundaryState extends PersistentState {
    public record Marker(BlockPos pos, String kind, String owner) {}
    public static final class Region {
        public String id, owner;
        public boolean closed, baseline;
        public int minY = -64, maxY = 320;
        public final List<BlockPos> vertices = new ArrayList<>();
        public List<Geometry.Point> polygon() { return vertices.stream().map(p -> new Geometry.Point(p.getX()+.5,p.getZ()+.5)).toList(); }
        public boolean contains(double x, double z) { return closed && Geometry.contains(polygon(),x,z); }
    }
    public final Map<Long, Marker> markers = new LinkedHashMap<>();
    public final Map<String, Region> regions = new LinkedHashMap<>();
    public long revision;
    public static BoundaryState get(ServerWorld world) { return world.getPersistentStateManager().getOrCreate(BoundaryState::read, BoundaryState::new, "japan_eez"); }
    public void changed() { revision++; markDirty(); }
    public static BoundaryState read(NbtCompound tag) {
        BoundaryState s = new BoundaryState();
        NbtList m = tag.getList("markers", NbtElement.COMPOUND_TYPE);
        for(int i=0;i<m.size();i++) { NbtCompound c=m.getCompound(i); BlockPos p=BlockPos.fromLong(c.getLong("pos")); s.markers.put(p.asLong(),new Marker(p,c.getString("kind"),c.getString("owner"))); }
        NbtList r=tag.getList("regions",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<r.size();i++) { NbtCompound c=r.getCompound(i); Region g=new Region();
            g.id=c.getString("id"); g.owner=c.getString("owner"); g.closed=c.getBoolean("closed"); g.baseline=c.getBoolean("baseline"); g.minY=c.getInt("minY"); g.maxY=c.getInt("maxY");
            for(long p:c.getLongArray("vertices")) g.vertices.add(BlockPos.fromLong(p)); s.regions.put(g.id,g);
        } return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag) {
        NbtList m=new NbtList();
        for(Marker v:markers.values()) { NbtCompound c=new NbtCompound(); c.putLong("pos",v.pos.asLong()); c.putString("kind",v.kind); c.putString("owner",v.owner); m.add(c); } tag.put("markers",m);
        NbtList r=new NbtList();
        for(Region g:regions.values()) { NbtCompound c=new NbtCompound(); c.putString("id",g.id); c.putString("owner",g.owner); c.putBoolean("closed",g.closed); c.putBoolean("baseline",g.baseline); c.putInt("minY",g.minY); c.putInt("maxY",g.maxY); c.putLongArray("vertices",g.vertices.stream().mapToLong(BlockPos::asLong).toArray()); r.add(c); } tag.put("regions",r);
        return tag;
    }
    public Region at(double x,double z) { for(Region g:regions.values()) if(g.contains(x,z)) return g; return null; }
    public String ownerAt(double x,double z) { Region g=at(x,z); return g==null ? JapanEez.config.defaultOwner : g.owner; }
}
