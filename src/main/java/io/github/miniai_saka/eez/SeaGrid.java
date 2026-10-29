package io.github.miniai_saka.eez;

import java.util.*;

/** Cell 0=land/beach, 1=ordinary ocean, 2=deep ocean. Coordinates are block cells. */
public final class SeaGrid {
    public final int n, step, x0, z0;
    public final byte[] biome;
    public final float[] distance;
    public final int[] nearest;
    public final boolean[] connectedShelf;
    public SeaGrid(int n,int step,int x0,int z0,byte[] biome) {
        this.n=n;this.step=step;this.x0=x0;this.z0=z0;this.biome=biome;
        distance=new float[n*n];nearest=new int[n*n];connectedShelf=new boolean[n*n];
        transform(); connect();
    }
    private static void edt(double[] f,double[] d,int[] labels,int n) {
        int[] v=new int[n]; double[] z=new double[n+1]; int k=-1;
        for(int q=0;q<n;q++) {
            if(f[q]>=1e18) continue;
            double s=Double.NEGATIVE_INFINITY;
            while(k>=0) { int p=v[k]; s=((f[q]+q*(double)q)-(f[p]+p*(double)p))/(2.0*(q-p)); if(s>z[k]) break; k--; }
            v[++k]=q; z[k]=k==0?Double.NEGATIVE_INFINITY:s; z[k+1]=Double.POSITIVE_INFINITY;
        }
        if(k<0) { Arrays.fill(d,1e18); Arrays.fill(labels,-1); return; }
        int best=0;
        for(int q=0;q<n;q++) { while(z[best+1]<q) best++; int p=v[best]; d[q]=(q-p)*(double)(q-p)+f[p]; labels[q]=p; }
    }
    private void transform() {
        double[] rows=new double[n*n], f=new double[n], d=new double[n]; int[] nearX=new int[n*n], l=new int[n];
        for(int z=0;z<n;z++) {
            for(int x=0;x<n;x++) f[x]=biome[z*n+x]==0?0:1e18;
            edt(f,d,l,n); for(int x=0;x<n;x++) { rows[z*n+x]=d[x]; nearX[z*n+x]=l[x]; }
        }
        for(int x=0;x<n;x++) {
            for(int z=0;z<n;z++) f[z]=rows[z*n+x]; edt(f,d,l,n);
            for(int z=0;z<n;z++) {
                int i=z*n+x, nz=l[z], nx=nz<0?-1:nearX[nz*n+x]; nearest[i]=nx<0?-1:nz*n+nx;
                distance[i]=nx<0?Float.POSITIVE_INFINITY:(float)Math.hypot(Math.max(0,Math.abs(x-nx)*step-step*.5),Math.max(0,Math.abs(z-nz)*step-step*.5));
            }
        }
    }
    private void connect() {
        int[] queue=new int[n*n];int head=0,tail=0;
        for(int z=1;z<n-1;z++) for(int x=1;x<n-1;x++) { int i=z*n+x;
            if(biome[i]==1 && (biome[i-1]==0||biome[i+1]==0||biome[i-n]==0||biome[i+n]==0)) { connectedShelf[i]=true;queue[tail++]=i; }
        }
        while(head<tail) { int i=queue[head++],x=i%n,z=i/n;
            if(x>0 && biome[i-1]==1 && !connectedShelf[i-1]) { connectedShelf[i-1]=true;queue[tail++]=i-1; }
            if(x<n-1 && biome[i+1]==1 && !connectedShelf[i+1]) { connectedShelf[i+1]=true;queue[tail++]=i+1; }
            if(z>0 && biome[i-n]==1 && !connectedShelf[i-n]) { connectedShelf[i-n]=true;queue[tail++]=i-n; }
            if(z<n-1 && biome[i+n]==1 && !connectedShelf[i+n]) { connectedShelf[i+n]=true;queue[tail++]=i+n; }
        }
    }
    public int index(double x,double z) { int a=(int)Math.floor((x-x0)/step),b=(int)Math.floor((z-z0)/step);return a<0||b<0||a>=n||b>=n?-1:b*n+a; }
    public double distanceAt(double x,double z) {
        int i=index(x,z);if(i<0||nearest[i]<0)return Double.POSITIVE_INFINITY;
        int seed=nearest[i],sx=seed%n,sz=seed/n;double best=Double.POSITIVE_INFINITY;
        for(int a=Math.max(0,sx-2);a<=Math.min(n-1,sx+2);a++)for(int b=Math.max(0,sz-2);b<=Math.min(n-1,sz+2);b++)if(biome[b*n+a]==0){
            double ax=x0+a*step,az=z0+b*step;
            best=Math.min(best,Math.hypot(Math.max(0,Math.max(ax-x,x-ax-step)),Math.max(0,Math.max(az-z,z-az-step))));
        }return best;
    }
    public boolean extended(int i,int eez,int max) { return biome[i]==1 && connectedShelf[i] && distance[i]>eez && distance[i]<=max; }
    public boolean[] mask(Layer layer,Config c) {
        boolean[] m=new boolean[n*n];
        for(int i=0;i<m.length;i++) m[i]=switch(layer) {
            case BASELINE -> biome[i]==0;
            case TERRITORIAL, AIRSPACE -> biome[i]==0 || distance[i]<=c.territorialDistance;
            case EEZ -> biome[i]==0 || distance[i]<=c.eezDistance;
            case SHELF -> biome[i]==0 || (biome[i]==1 && connectedShelf[i] && distance[i]<=(c.extendedShelf?c.extendedDistance:c.eezDistance));
            default -> false;
        }; return m;
    }
}
