package io.github.miniai_saka.eez;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeaGridTest {
    private SeaGrid coast(){int n=380;byte[] b=new byte[n*n];for(int z=0;z<n;z++)for(int x=10;x<n;x++)b[z*n+x]=1;return new SeaGrid(n,1,0,0,b);}
    @Test void nauticalDistancesUse12And200And350Blocks(){
        SeaGrid g=coast();int row=190*g.n;assertEquals(11.5,g.distance[row+21],1e-5);assertEquals(12.5,g.distance[row+22],1e-5);
        assertTrue(g.distance[row+209]<=200);assertTrue(g.distance[row+210]>200);assertTrue(g.extended(row+210,200,350));assertTrue(g.extended(row+359,200,350));assertFalse(g.extended(row+360,200,350));
    }
    @Test void deepOceanBreaksShelfContinuity(){
        int n=80;byte[] b=new byte[n*n];for(int z=0;z<n;z++)for(int x=5;x<n;x++)b[z*n+x]=(byte)(x>=40&&x<=44?2:1);
        SeaGrid g=new SeaGrid(n,4,0,0,b);assertTrue(g.connectedShelf[40*n+30]);assertFalse(g.connectedShelf[40*n+42]);assertFalse(g.connectedShelf[40*n+55]);assertFalse(g.extended(40*n+55,100,350));
    }
    @Test void oceanWithoutCoastCannotClaimWholeWorld(){int n=20;byte[] b=new byte[n*n];Arrays.fill(b,(byte)1);SeaGrid g=new SeaGrid(n,4,-40,-40,b);assertEquals(Float.POSITIVE_INFINITY,g.distance[200]);assertEquals(-1,g.nearest[200]);assertFalse(g.connectedShelf[200]);}
    @Test void noSeamAtNegativeCoordinates(){SeaGrid g=coast();assertEquals(-1,g.index(-.01,1));assertEquals(0,g.index(0,0));assertEquals(-1,g.index(380,1));}
    @Test void playerPositionUsesExactDistanceWithinSurveyCell(){SeaGrid g=coast();assertEquals(12,g.distanceAt(22,190.2),1e-7);assertTrue(g.distanceAt(22.001,190.2)>12);assertEquals(200,g.distanceAt(210,190.2),1e-7);assertEquals(350,g.distanceAt(360,190.2),1e-7);}
    @Test void nearestLandTransformMatchesBruteForce(){
        int n=21;byte[] b=new byte[n*n];Arrays.fill(b,(byte)1);int[] seeds={2*n+3,8*n+15,18*n+6};for(int i:seeds)b[i]=0;SeaGrid g=new SeaGrid(n,4,0,0,b);
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){double best=Double.POSITIVE_INFINITY;for(int i:seeds)best=Math.min(best,Math.pow(x-i%n,2)+Math.pow(z-i/n,2));int near=g.nearest[z*n+x];assertEquals(best,Math.pow(x-near%n,2)+Math.pow(z-near/n,2),1e-8);}
    }
}
