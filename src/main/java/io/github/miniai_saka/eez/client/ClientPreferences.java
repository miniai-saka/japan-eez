package io.github.miniai_saka.eez.client;

import io.github.miniai_saka.eez.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ClientPreferences {
    public Map<String,Boolean> visible=new LinkedHashMap<>();
    public boolean customHeight=false;
    public int bottomY=-64,topY=320;
    public boolean hud=true;
    private static Path file(){return FabricLoader.getInstance().getConfigDir().resolve("japan-eez-client.json");}
    public ClientPreferences(){for(Layer l:Layer.values())visible.put(l.key,true);}
    public boolean enabled(Layer l){return visible.getOrDefault(l.key,true);}
    public static ClientPreferences load(){try{if(Files.exists(file())){var p=Config.GSON.fromJson(Files.readString(file(),StandardCharsets.UTF_8),ClientPreferences.class);if(p!=null&&p.visible!=null&&p.bottomY>=-2048&&p.topY<=4096&&p.bottomY<p.topY)return p;}}catch(Exception e){JapanEez.LOG.warn("Could not load client display settings",e);}return new ClientPreferences();}
    public void save(){try{Files.createDirectories(file().getParent());Files.writeString(file(),Config.GSON.toJson(this),StandardCharsets.UTF_8);}catch(Exception e){JapanEez.LOG.warn("Could not save client display settings",e);}}
}
