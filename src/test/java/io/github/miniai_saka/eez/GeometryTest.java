package io.github.miniai_saka.eez;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    @Test void polygonIncludesBoundaryAndRejectsBowtie(){
        var p=List.of(new Geometry.Point(0,0),new Geometry.Point(10,0),new Geometry.Point(10,10),new Geometry.Point(0,10));
        assertTrue(Geometry.validPolygon(p));assertTrue(Geometry.contains(p,5,5));assertTrue(Geometry.contains(p,0,5));assertFalse(Geometry.contains(p,10.1,5));
        assertFalse(Geometry.validPolygon(List.of(p.get(0),p.get(2),p.get(1),p.get(3))));
    }
    @Test void polygonRejectsCollinearRepeatedAndOverlappingEdges(){
        assertFalse(Geometry.validPolygon(List.of(new Geometry.Point(0,0),new Geometry.Point(1,0),new Geometry.Point(2,0))));
        assertFalse(Geometry.validPolygon(List.of(new Geometry.Point(0,0),new Geometry.Point(5,0),new Geometry.Point(3,0),new Geometry.Point(3,5))));
    }
    @Test void straightContourIsMergedWithoutArtificialWindowEdges(){
        int n=10;boolean[] mask=new boolean[n*n];for(int z=0;z<n;z++)for(int x=0;x<5;x++)mask[z*n+x]=true;
        var edges=Geometry.outline(mask,n,0,0,1,1,9);assertEquals(1,edges.size());assertEquals(new Geometry.Edge(5,1,5,9),edges.get(0));
    }
    @Test void segmentDistanceClampsToEndpoints(){assertEquals(3,Geometry.segmentDistance(5,3,0,0,10,0),1e-8);assertEquals(5,Geometry.segmentDistance(-3,4,0,0,10,0),1e-8);}
}
