package io.github.miniai_saka.eez;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.lang.reflect.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class Commands {
    private static final SimpleCommandExceptionType NO_REGION=new SimpleCommandExceptionType(Text.literal("指定した領域がありません。"));
    private static final DynamicCommandExceptionType ERROR=new DynamicCommandExceptionType(o->Text.literal(String.valueOf(o)));
    private static String str(CommandContext<ServerCommandSource> c,String key) {return StringArgumentType.getString(c,key);}
    private static BoundaryState state(CommandContext<ServerCommandSource> c) {return BoundaryState.get(c.getSource().getWorld());}
    private static BoundaryState.Region region(CommandContext<ServerCommandSource> c) throws CommandSyntaxException { var r=state(c).regions.get(str(c,"id"));if(r==null)throw NO_REGION.create();return r; }
    private static int ok(CommandContext<ServerCommandSource> c,String text) { c.getSource().sendFeedback(()->Text.literal(text),false);return 1; }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)-> dispatcher.register(literal("jpboundary")
            .executes(c->ok(c,"/jpboundary tool | here | config | region | marker | refresh / ツールを持って右クリックで表示GUI"))
            .then(literal("tool").executes(c->{c.getSource().getPlayerOrThrow().giveItemStack(new net.minecraft.item.ItemStack(JapanEez.TOOL));return 1;}))
            .then(literal("here").executes(c->{var p=c.getSource().getPlayerOrThrow();JapanEez.updateStatus(p,JapanEez.session(p));return ok(c,JapanEez.session(p).status);} ))
            .then(literal("refresh").requires(s->s.hasPermissionLevel(2)).executes(c->{JapanEez.invalidate();return ok(c,"海岸線を再調査します。");}))
            .then(literal("config").requires(s->s.hasPermissionLevel(2))
                .executes(c->ok(c,Config.GSON.toJson(JapanEez.config)))
                .then(literal("reload").executes(c->{try { Config candidate=Config.load();JapanEez.config=candidate;JapanEez.invalidate();return ok(c,"設定を再読み込みしました。"); }catch(Exception e){throw ERROR.create(e.getMessage());}}))
                .then(literal("set").then(argument("key",StringArgumentType.word()).suggests((c,b)->{for(Field f:Config.class.getFields())if(!Modifier.isStatic(f.getModifiers())&&(f.getType()==int.class||f.getType()==boolean.class||f.getType()==String.class))b.suggest(f.getName());return b.buildFuture();})
                    .then(argument("value",StringArgumentType.greedyString()).executes(Commands::setConfig))))
                .then(literal("color").then(argument("layer",StringArgumentType.word()).suggests((c,b)->{for(Layer l:Layer.values())b.suggest(l.key);return b.buildFuture();})
                    .then(argument("hex",StringArgumentType.word()).executes(c->{Config n=copy();String l=str(c,"layer");if(!n.colors.containsKey(l))throw ERROR.create("不明な表示レイヤーです。");try{String hex=str(c,"hex").replace("#","");if(!hex.matches("[0-9a-fA-F]{6}"))throw new NumberFormatException();n.colors.put(l,Integer.parseInt(hex,16));return save(c,n);}catch(NumberFormatException e){throw ERROR.create("色は6桁の16進数です。");}})))))
            .then(literal("marker").requires(s->s.hasPermissionLevel(2))
                .then(literal("enabled").then(argument("kind",StringArgumentType.word()).suggests((c,b)->{JapanEez.MARKERS.keySet().forEach(b::suggest);return b.buildFuture();})
                    .then(argument("enabled",BoolArgumentType.bool()).executes(c->{Config n=copy();if(!n.markerEnabled.containsKey(str(c,"kind")))throw ERROR.create("不明な測量標です。");n.markerEnabled.put(str(c,"kind"),BoolArgumentType.getBool(c,"enabled"));return save(c,n);}))))
                .then(literal("owner").then(argument("owner",StringArgumentType.greedyString()).executes(c->{var p=c.getSource().getPlayerOrThrow();BlockPos pos=JapanEez.selectedMarkers.get(p.getUuid());BoundaryState s=state(c);var m=pos==null?null:s.markers.get(pos.asLong());if(m==null)throw ERROR.create("ツールで測量標を選択してください。");String owner=str(c,"owner");if(owner.length()>64)throw ERROR.create("名前は64文字以内です。");s.markers.put(pos.asLong(),new BoundaryState.Marker(pos,m.kind(),owner));s.changed();return ok(c,"基準点の所有者: "+owner);}))))
            .then(literal("region").requires(s->s.hasPermissionLevel(2))
                .then(literal("list").executes(c->{for(var r:state(c).regions.values())ok(c,r.id+" / "+r.owner+" / "+r.vertices.size()+"頂点 / "+(r.closed?"確定":"編集中")+" / Y="+r.minY+".."+r.maxY);return 1;}))
                .then(literal("create").then(argument("id",StringArgumentType.word()).then(argument("owner",StringArgumentType.greedyString()).executes(c->{
                    BoundaryState s=state(c);String id=str(c,"id"),owner=str(c,"owner");
                    if(!id.matches("[a-zA-Z0-9_-]{1,48}")||owner.length()>64)throw ERROR.create("IDは英数字_-で48文字以内、領有者は64文字以内です。");
                    if(s.regions.containsKey(id)||s.regions.size()>=JapanEez.config.maxRegions)throw ERROR.create("IDが重複しているか、領域数の上限です。");
                    var r=new BoundaryState.Region();r.id=id;r.owner=owner;r.minY=JapanEez.config.undergroundBottomY;r.maxY=JapanEez.config.airspaceTopY;s.regions.put(id,r);s.changed();
                    if(c.getSource().getEntity() instanceof ServerPlayerEntity p)JapanEez.activeRegions.put(p.getUuid(),id);return ok(c,"領域 "+id+" を作成。ツールで測量標をShift+右クリックし、順番に3点以上追加してください。");}))))
                .then(literal("select").then(argument("id",StringArgumentType.word()).executes(c->{var r=region(c);JapanEez.activeRegions.put(c.getSource().getPlayerOrThrow().getUuid(),r.id);return ok(c,"編集対象: "+r.id);})))
                .then(literal("baseline").then(argument("id",StringArgumentType.word()).then(argument("enabled",BoolArgumentType.bool()).executes(c->{var r=region(c);r.baseline=BoolArgumentType.getBool(c,"enabled");state(c).changed();return ok(c,"測量基準線の表示: "+r.baseline);}))))
                .then(literal("vertex").then(argument("id",StringArgumentType.word()).then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->{addVertex(region(c),BlockPosArgumentType.getBlockPos(c,"pos"));state(c).changed();return ok(c,"頂点を追加しました。");}))))
                .then(literal("undo").then(argument("id",StringArgumentType.word()).executes(c->{var r=region(c);if(!r.vertices.isEmpty())r.vertices.remove(r.vertices.size()-1);r.closed=false;state(c).changed();return ok(c,"最後の頂点を取り消しました。");})))
                .then(literal("close").then(argument("id",StringArgumentType.word()).executes(c->{var r=region(c);if(!Geometry.validPolygon(r.polygon()))throw ERROR.create("3点以上の、自己交差しない面積のある多角形が必要です。");r.closed=true;state(c).changed();JapanEez.invalidate();return ok(c,"領有境界を確定しました: "+r.owner);})))
                .then(literal("owner").then(argument("id",StringArgumentType.word()).then(argument("owner",StringArgumentType.greedyString()).executes(c->{var r=region(c);String owner=str(c,"owner");if(owner.length()>64)throw ERROR.create("64文字以内で指定してください。");r.owner=owner;state(c).changed();return ok(c,"領有者: "+r.owner);}))))
                .then(literal("height").then(argument("id",StringArgumentType.word()).then(argument("minY",IntegerArgumentType.integer(-2048,4095)).then(argument("maxY",IntegerArgumentType.integer(-2047,4096)).executes(c->{var r=region(c);int min=IntegerArgumentType.getInteger(c,"minY"),max=IntegerArgumentType.getInteger(c,"maxY");if(max<=min)throw ERROR.create("上限は下限より大きくしてください。");r.minY=min;r.maxY=max;state(c).changed();return ok(c,"領域高度: "+min+"〜"+max);} )))))
                .then(literal("delete").then(argument("id",StringArgumentType.word()).executes(c->{var r=region(c);state(c).regions.remove(r.id);state(c).changed();return ok(c,"領域を削除しました: "+r.id);})))
                .then(literal("export").executes(c->RegionFiles.exportFile(c.getSource())))
                .then(literal("import").executes(c->RegionFiles.importFile(c.getSource())))
            )
        ));
    }
    public static void addVertex(BoundaryState.Region r,BlockPos pos) throws CommandSyntaxException {
        if(r.vertices.size()>=JapanEez.config.maxVertices)throw ERROR.create("頂点数の上限です。");
        if(r.vertices.stream().anyMatch(p->p.getX()==pos.getX()&&p.getZ()==pos.getZ()))throw ERROR.create("同じXZの頂点があります。");
        r.vertices.add(pos.toImmutable());r.closed=false;
    }
    public static void addSelected(ServerPlayerEntity p) {
        if(!p.hasPermissionLevel(2)){p.sendMessage(Text.literal("領域の編集には権限レベル2が必要です。"),true);return;}
        BoundaryState s=BoundaryState.get(p.getServerWorld());var r=s.regions.get(JapanEez.activeRegions.get(p.getUuid()));
        try {if(r==null)throw NO_REGION.create();addVertex(r,JapanEez.selectedMarkers.get(p.getUuid()));s.changed();p.sendMessage(Text.literal(r.id+": "+r.vertices.size()+"頂点。/jpboundary region close "+r.id+" で確定"),true);}catch(CommandSyntaxException e){p.sendMessage(Text.literal(e.getRawMessage().getString()),true);}
    }
    private static Config copy() {return Config.GSON.fromJson(Config.GSON.toJson(JapanEez.config),Config.class);}
    private static int setConfig(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        Config n=copy();try {
            Field f=Config.class.getField(str(c,"key"));if(Modifier.isStatic(f.getModifiers()))throw new IllegalArgumentException("変更できない項目です。");
            String v=str(c,"value");if(f.getType()==boolean.class){if(!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException("true / falseで指定してください。");f.setBoolean(n,Boolean.parseBoolean(v));}
            else if(f.getType()==int.class)f.setInt(n,Integer.parseInt(v));else if(f.getType()==String.class)f.set(n,v);else throw new IllegalArgumentException("この項目は設定ファイルで編集してください。");
            return save(c,n);
        }catch(Exception e){throw ERROR.create("設定エラー: "+e.getMessage());}
    }
    private static int save(CommandContext<ServerCommandSource> c,Config n) throws CommandSyntaxException {
        try{n.save();JapanEez.config=n;JapanEez.invalidate();return ok(c,"設定を保存しました。");}catch(Exception e){throw ERROR.create(e.getMessage());}
    }
}
