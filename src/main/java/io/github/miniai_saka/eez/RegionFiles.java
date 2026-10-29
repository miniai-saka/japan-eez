package io.github.miniai_saka.eez;

import com.google.gson.*;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class RegionFiles {
    private static Path path(ServerCommandSource s) { return Config.FILE.resolveSibling("japan-eez-regions-"+s.getWorld().getRegistryKey().getValue().toString().replace(':','_').replace('/','_')+".json"); }
    private static CommandSyntaxException error(Exception e) {return new SimpleCommandExceptionType(Text.literal("領域ファイル: "+e.getMessage())).create();}
    public static int exportFile(ServerCommandSource s) throws CommandSyntaxException {
        try {
            BoundaryState state=BoundaryState.get(s.getWorld());JsonObject root=new JsonObject();root.addProperty("dimension",s.getWorld().getRegistryKey().getValue().toString());JsonArray regions=new JsonArray();
            for(var r:state.regions.values()) {JsonObject g=new JsonObject();g.addProperty("id",r.id);g.addProperty("owner",r.owner);g.addProperty("closed",r.closed);g.addProperty("minY",r.minY);g.addProperty("maxY",r.maxY);
                JsonArray vertices=new JsonArray();for(BlockPos p:r.vertices){JsonArray v=new JsonArray();v.add(p.getX());v.add(p.getY());v.add(p.getZ());vertices.add(v);}g.add("vertices",vertices);regions.add(g);}
            int index=0;for(var r:state.regions.values())regions.get(index++).getAsJsonObject().addProperty("baseline",r.baseline);
            root.add("regions",regions);JsonArray markers=new JsonArray();for(var m:state.markers.values()){JsonObject g=new JsonObject();g.addProperty("x",m.pos().getX());g.addProperty("y",m.pos().getY());g.addProperty("z",m.pos().getZ());g.addProperty("owner",m.owner());markers.add(g);}root.add("markerOwners",markers);
            Files.writeString(path(s),Config.GSON.toJson(root),StandardCharsets.UTF_8);s.sendFeedback(()->Text.literal("書き出し: "+path(s)),false);return 1;
        }catch(Exception e){throw error(e);}
    }
    public static int importFile(ServerCommandSource s) throws CommandSyntaxException {
        try {
            Path file=path(s);if(Files.size(file)>4_000_000)throw new IllegalArgumentException("ファイルは4MB以内です。");
            JsonObject root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!root.get("dimension").getAsString().equals(s.getWorld().getRegistryKey().getValue().toString()))throw new IllegalArgumentException("ディメンションが一致しません。");
            JsonArray array=root.getAsJsonArray("regions");if(array.size()>JapanEez.config.maxRegions)throw new IllegalArgumentException("領域数が上限を超えています。");
            Map<String,BoundaryState.Region> candidates=new LinkedHashMap<>();
            for(JsonElement element:array){JsonObject g=element.getAsJsonObject();var r=new BoundaryState.Region();r.id=g.get("id").getAsString();r.owner=g.get("owner").getAsString();r.closed=g.get("closed").getAsBoolean();r.minY=g.get("minY").getAsInt();r.maxY=g.get("maxY").getAsInt();
                if(!r.id.matches("[a-zA-Z0-9_-]{1,48}")||r.owner.isBlank()||r.owner.length()>64||r.minY< -2048||r.maxY>4096||r.minY>=r.maxY||candidates.containsKey(r.id))throw new IllegalArgumentException("ID・領有者・高度が不正です。");
                for(JsonElement v:g.getAsJsonArray("vertices")){JsonArray a=v.getAsJsonArray();int x=a.get(0).getAsInt(),y=a.get(1).getAsInt(),z=a.get(2).getAsInt();if(Math.abs((long)x)>30000000||Math.abs((long)z)>30000000||y< -2048||y>4096)throw new IllegalArgumentException("座標が範囲外です。");Commands.addVertex(r,new BlockPos(x,y,z));}
                // addVertex intentionally opens polygons; restore only after validating the complete file.
                r.closed=g.get("closed").getAsBoolean();r.baseline=g.has("baseline")&&g.get("baseline").getAsBoolean();if(r.closed&&!Geometry.validPolygon(r.polygon()))throw new IllegalArgumentException("領域の頂点が不正です。");candidates.put(r.id,r);
            }
            BoundaryState state=BoundaryState.get(s.getWorld());Map<Long,BoundaryState.Marker> owners=new LinkedHashMap<>();
            if(root.has("markerOwners"))for(JsonElement element:root.getAsJsonArray("markerOwners")){JsonObject g=element.getAsJsonObject();BlockPos p=new BlockPos(g.get("x").getAsInt(),g.get("y").getAsInt(),g.get("z").getAsInt());String owner=g.get("owner").getAsString();if(owner.isBlank()||owner.length()>64)throw new IllegalArgumentException("基準点の所有者が不正です。");var m=state.markers.get(p.asLong());if(m!=null)owners.put(p.asLong(),new BoundaryState.Marker(p,m.kind(),owner));}
            state.regions.clear();state.regions.putAll(candidates);state.markers.putAll(owners);state.changed();JapanEez.invalidate();s.sendFeedback(()->Text.literal("領域設定を読み込みました: "+candidates.size()+"件"),false);return 1;
        }catch(Exception e){throw error(e);}
    }
}
