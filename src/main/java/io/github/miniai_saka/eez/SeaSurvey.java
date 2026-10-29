package io.github.miniai_saka.eez;

import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public final class SeaSurvey {
    private final Map<Long,Byte> cache=new LinkedHashMap<>(4096,.75f,true) { @Override protected boolean removeEldestEntry(Map.Entry<Long,Byte> e) { return size()>400000; } };
    public final class Job {
        public final int cx,cz,n,step,x0,z0,margin;
        public final long revision;
        private final byte[] cells;
        private int cursor;
        public SeaGrid result;
        public Job(int x,int z) {
            Config c=JapanEez.config;step=c.surveyStep;cx=Math.floorDiv(x,32)*32+16;cz=Math.floorDiv(z,32)*32+16;
            margin=(int)Math.ceil((c.extendedDistance+step*2)/(double)step);
            n=2*((c.renderRadius+16+step-1)/step+margin)+1;x0=cx-(n/2)*step;z0=cz-(n/2)*step;
            cells=new byte[n*n];revision=JapanEez.revision;
        }
        public boolean advance(ServerWorld world,int budget) {
            int end=Math.min(cells.length,cursor+budget);
            for(;cursor<end;cursor++) { int x=x0+(cursor%n)*step+step/2,z=z0+(cursor/n)*step+step/2;cells[cursor]=sample(world,x,z); }
            if(cursor==cells.length && result==null) result=new SeaGrid(n,step,x0,z0,cells);
            return result!=null;
        }
        public int progress() { return (int)(100L*cursor/cells.length); }
    }
    public byte sample(ServerWorld world,int x,int z) {
        long key=BlockPos.asLong(Math.floorDiv(x,4),0,Math.floorDiv(z,4));Byte v=cache.get(key);if(v!=null)return v;
        // Noise biome lookup deliberately avoids chunk creation, including ungenerated coasts.
        var entry=world.getChunkManager().getChunkGenerator().getBiomeSource().getBiome(Math.floorDiv(x,4),Math.floorDiv(world.getSeaLevel(),4),Math.floorDiv(z,4),world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
        String id=entry.getKey().map(k->k.getValue().toString()).orElse(""); Config c=JapanEez.config;
        byte kind=(byte)(entry.isIn(BiomeTags.IS_DEEP_OCEAN)||c.extraDeepBiomes.contains(id)?2:entry.isIn(BiomeTags.IS_OCEAN)||c.extraShallowBiomes.contains(id)?1:0);
        cache.put(key,kind);return kind;
    }
    public void clear() { cache.clear(); }
}
