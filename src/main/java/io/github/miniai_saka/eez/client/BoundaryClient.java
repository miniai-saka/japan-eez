package io.github.miniai_saka.eez.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.miniai_saka.eez.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class BoundaryClient implements ClientModInitializer {
    public static ClientPreferences preferences;
    public static List<JapanEez.Span> spans=List.of();
    public static String status="サーバーの調査結果を待っています…",dimension="";
    public static int progress,bottom=-64,top=320,airTop=320;
    public static int territorialDistance=12,eezDistance=200,extendedDistance=350;
    public static boolean extendedShelf=true;
    public static boolean through=true;
    public static int[] colors=new int[Layer.values().length];
    private static KeyBinding key;
    @Override public void onInitializeClient() {
        preferences=ClientPreferences.load();
        for(Layer l:Layer.values())colors[l.ordinal()]=new Config().colors.get(l.key);
        key=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.japan_eez.settings",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_B,"category.japan_eez"));
        UseItemCallback.EVENT.register((player,world,hand)->{if(world.isClient&&player.getStackInHand(hand).isOf(JapanEez.TOOL)){openSettings();return TypedActionResult.success(player.getStackInHand(hand));}return TypedActionResult.pass(player.getStackInHand(hand));});
        ClientTickEvents.END_CLIENT_TICK.register(client->{while(key.wasPressed())if(holding())openSettings();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{spans=List.of();dimension="";status="サーバーの調査結果を待っています…";progress=0;});
        ClientPlayNetworking.registerGlobalReceiver(JapanEez.SNAPSHOT,(client,handler,buf,sender)->{
            String dim=buf.readString(256),text=buf.readString(1024);int pct=buf.readInt(),lo=buf.readInt(),hi=buf.readInt(),air=buf.readInt();boolean xray=buf.readBoolean();
            int td=buf.readInt(),ed=buf.readInt(),xd=buf.readInt();boolean xs=buf.readBoolean();int[] rgb=new int[Layer.values().length];for(int i=0;i<rgb.length;i++)rgb[i]=buf.readInt();
            int count=buf.readInt();if(count<0||count>8192)return;List<JapanEez.Span> list=new ArrayList<>(count);
            for(int i=0;i<count;i++){int layer=buf.readUnsignedByte();double x1=buf.readDouble(),z1=buf.readDouble(),x2=buf.readDouble(),z2=buf.readDouble(),low=buf.readDouble(),high=buf.readDouble();boolean box=buf.readBoolean();
                if(layer>=Layer.values().length||!Double.isFinite(x1+z1+x2+z2+low+high))return;list.add(new JapanEez.Span(Layer.values()[layer],x1,z1,x2,z2,low,high,box));}
            client.execute(()->{dimension=dim;status=text;progress=pct;bottom=lo;top=hi;airTop=air;through=xray;territorialDistance=td;eezDistance=ed;extendedDistance=xd;extendedShelf=xs;colors=rgb;spans=List.copyOf(list);});
        });
        WorldRenderEvents.LAST.register(BoundaryClient::render);
        HudRenderCallback.EVENT.register((draw,delta)->{
            MinecraftClient c=MinecraftClient.getInstance();if(!holding()||!preferences.hud||c.options.hudHidden)return;
            String text="[境界] "+status+(progress<100?"  調査 "+progress+"%":"");
            int max=c.getWindow().getScaledWidth()-16;String shown=c.textRenderer.trimToWidth(text,max);
            draw.fill(5,5,Math.min(max+5,c.textRenderer.getWidth(shown)+12),36,0xA0101828);
            draw.drawTextWithShadow(c.textRenderer,shown,9,9,0xFFFFFF);
            draw.drawTextWithShadow(c.textRenderer,"右クリック / B: 表示設定  |  描画 "+spans.size()+"線分",9,23,0xB8D9EF);
        });
    }
    public static boolean holding(){var p=MinecraftClient.getInstance().player;return p!=null&&(p.getMainHandStack().isOf(JapanEez.TOOL)||p.getOffHandStack().isOf(JapanEez.TOOL));}
    public static void openSettings(){MinecraftClient c=MinecraftClient.getInstance();c.execute(()->c.setScreen(new BoundaryScreen(c.currentScreen)));}
    private static void vertex(BufferBuilder b,Matrix4f m,double x,double y,double z,float r,float g,float blue){b.vertex(m,(float)x,(float)y,(float)z).color(r,g,blue,.85f).next();}
    private static void line(BufferBuilder b,Matrix4f m,double x1,double y1,double z1,double x2,double y2,double z2,float r,float g,float blue){vertex(b,m,x1,y1,z1,r,g,blue);vertex(b,m,x2,y2,z2,r,g,blue);}
    private static void render(WorldRenderContext context){
        MinecraftClient c=MinecraftClient.getInstance();if(!holding()||c.world==null||c.options.hudHidden||spans.isEmpty()||!dimension.equals(c.world.getRegistryKey().getValue().toString()))return;
        var matrices=context.matrixStack();matrices.push();Vec3d cam=context.camera().getPos();matrices.translate(-cam.x,-cam.y,-cam.z);Matrix4f m=matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();if(through)RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);int vertices=0;
        for(JapanEez.Span t:spans){if(!preferences.enabled(t.layer()))continue;
            double lo=t.low(),hi=t.high();if(preferences.customHeight){lo=Math.max(lo,preferences.bottomY);hi=Math.min(hi,preferences.topY);}if(t.layer()==Layer.AIRSPACE)hi=Math.min(hi,airTop);if(hi<lo)continue;
            int rgb=colors[t.layer().ordinal()];float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,blue=(rgb&255)/255f;double x1=t.x1(),z1=t.z1(),x2=t.x2(),z2=t.z2();
            if(!t.box()){line(b,m,x1,lo,z1,x2,lo,z2,r,g,blue);vertices+=2;}
            if(hi>lo){if(!t.box()){line(b,m,x1,hi,z1,x2,hi,z2,r,g,blue);vertices+=2;}line(b,m,x1,lo,z1,x1,hi,z1,r,g,blue);line(b,m,x2,lo,z2,x2,hi,z2,r,g,blue);vertices+=4;}
            if(t.box()){
                line(b,m,x1,lo,z1,x1,lo,z2,r,g,blue);line(b,m,x1,lo,z2,x2,lo,z2,r,g,blue);line(b,m,x2,lo,z2,x2,lo,z1,r,g,blue);line(b,m,x2,lo,z1,x1,lo,z1,r,g,blue);
                line(b,m,x1,hi,z1,x1,hi,z2,r,g,blue);line(b,m,x1,hi,z2,x2,hi,z2,r,g,blue);line(b,m,x2,hi,z2,x2,hi,z1,r,g,blue);line(b,m,x2,hi,z1,x1,hi,z1,r,g,blue);
                line(b,m,x1,lo,z2,x1,hi,z2,r,g,blue);line(b,m,x2,lo,z1,x2,hi,z1,r,g,blue);vertices+=20;
            }
        }
        if(vertices>0)BufferRenderer.drawWithGlobalProgram(b.end());else b.end().release();
        RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.disableBlend();matrices.pop();
    }
}
