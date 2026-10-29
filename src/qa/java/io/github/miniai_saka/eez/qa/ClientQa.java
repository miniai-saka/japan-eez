package io.github.miniai_saka.eez.qa;

import io.github.miniai_saka.eez.*;
import io.github.miniai_saka.eez.client.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import java.util.*;

public final class ClientQa implements ClientModInitializer {
    private int ticks;
    private boolean checked;
    private boolean saw192,saw320;
    @Override public void onInitializeClient(){if(!Boolean.getBoolean("eez.qa"))return;
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.player==null||c.world==null)return;ticks++;
            if(BoundaryClient.airTop==192&&!saw192){if(BoundaryClient.extendedShelf)throw new AssertionError("Extended setting did not synchronize");saw192=true;JapanEez.LOG.info("EEZ_QA PASS CLIENT config sync airTop=192 extended=false");}
            if(saw192&&BoundaryClient.airTop==320&&!saw320){if(!BoundaryClient.extendedShelf)throw new AssertionError("Extended restoration did not synchronize");saw320=true;JapanEez.LOG.info("EEZ_QA PASS CLIENT config restore airTop=320 extended=true");}
            if(!checked&&ticks>160&&!BoundaryClient.spans.isEmpty()){
                checked=true;for(var block:JapanEez.MARKERS.values())if(c.getBlockRenderManager().getModel(block.getDefaultState())==c.getBakedModelManager().getMissingModel())throw new AssertionError("Missing marker model "+block.kind);
                JapanEez.LOG.info("EEZ_QA CLIENT_RECEIVED spans={} status={} airTop={}",BoundaryClient.spans.size(),BoundaryClient.status,BoundaryClient.airTop);
                ScreenshotRecorder.saveScreenshot(c.runDirectory,"qa-world.png",c.getFramebuffer(),t->JapanEez.LOG.info("EEZ_QA screenshot {}",t.getString()));
            }
            if(checked&&ticks==500)ScreenshotRecorder.saveScreenshot(c.runDirectory,"qa-world-clean.png",c.getFramebuffer(),t->JapanEez.LOG.info("EEZ_QA screenshot {}",t.getString()));
            if(checked&&ticks==550)BoundaryClient.openSettings();
            if(checked&&ticks==570)ScreenshotRecorder.saveScreenshot(c.runDirectory,"qa-gui.png",c.getFramebuffer(),t->JapanEez.LOG.info("EEZ_QA screenshot {}",t.getString()));
        });
    }
}
