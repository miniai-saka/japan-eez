package io.github.miniai_saka.eez;

import java.util.*;

/** Pure, deterministic geometry, independent of rendering and chunk loading. */
public final class Geometry {
    public record Point(double x, double z) {}
    public record Edge(double x1, double z1, double x2, double z2) {}
    public static double segmentDistance(double x, double z, double ax, double az, double bx, double bz) {
        double dx=bx-ax, dz=bz-az, l=dx*dx+dz*dz;
        double t=l==0 ? 0 : Math.max(0, Math.min(1, ((x-ax)*dx+(z-az)*dz)/l));
        return Math.hypot(x-ax-t*dx, z-az-t*dz);
    }
    public static boolean contains(List<Point> p, double x, double z) {
        boolean in=false;
        for (int i=0,j=p.size()-1;i<p.size();j=i++) {
            Point a=p.get(j), b=p.get(i);
            if (segmentDistance(x,z,a.x,a.z,b.x,b.z)<1e-7) return true;
            if ((a.z>z)!=(b.z>z) && x<(b.x-a.x)*(z-a.z)/(b.z-a.z)+a.x) in=!in;
        } return in;
    }
    private static double cross(Point a, Point b, Point c) { return (b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x); }
    private static boolean intersects(Point a, Point b, Point c, Point d) {
        double a1=cross(a,b,c), a2=cross(a,b,d), a3=cross(c,d,a), a4=cross(c,d,b);
        if (a1*a2<0 && a3*a4<0) return true;
        return (Math.abs(a1)<1e-8 && segmentDistance(c.x,c.z,a.x,a.z,b.x,b.z)<1e-8)
            || (Math.abs(a2)<1e-8 && segmentDistance(d.x,d.z,a.x,a.z,b.x,b.z)<1e-8)
            || (Math.abs(a3)<1e-8 && segmentDistance(a.x,a.z,c.x,c.z,d.x,d.z)<1e-8)
            || (Math.abs(a4)<1e-8 && segmentDistance(b.x,b.z,c.x,c.z,d.x,d.z)<1e-8);
    }
    public static boolean validPolygon(List<Point> p) {
        if (p.size()<3 || new HashSet<>(p).size()!=p.size()) return false;
        double area=0;
        for(int i=0;i<p.size();i++) {
            Point a=p.get(i),b=p.get((i+1)%p.size()); area+=a.x*b.z-b.x*a.z;
            for(int j=i+1;j<p.size();j++) if (j!=i+1 && !(i==0 && j==p.size()-1)
                && intersects(a,b,p.get(j),p.get((j+1)%p.size()))) return false;
        } return Math.abs(area)>0.01;
    }
    public static List<Edge> outline(boolean[] mask, int n, int x0, int z0, int step, int min, int max) {
        List<Edge> result=new ArrayList<>();
        // Merge collinear raster edges to keep the network and renderer bounded.
        for(int z=min;z<=max;z++) for(int x=min;x<max;) {
            boolean south=z<n && mask[z*n+x], north=z>0 && mask[(z-1)*n+x];
            if(south==north) { x++; continue; }
            int a=x++;
            while(x<max && ((z<n && mask[z*n+x])==(south)) && ((z>0 && mask[(z-1)*n+x])==north)) x++;
            result.add(new Edge(x0+a*step,z0+z*step,x0+x*step,z0+z*step));
        }
        for(int x=min;x<=max;x++) for(int z=min;z<max;) {
            boolean east=x<n && mask[z*n+x], west=x>0 && mask[z*n+x-1];
            if(east==west) { z++; continue; }
            int a=z++;
            while(z<max && ((x<n && mask[z*n+x])==east) && ((x>0 && mask[z*n+x-1])==west)) z++;
            result.add(new Edge(x0+x*step,z0+a*step,x0+x*step,z0+z*step));
        } return result;
    }
    private Geometry() {}
}
