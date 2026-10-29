package io.github.miniai_saka.eez.client;

import io.github.miniai_saka.eez.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

public final class BoundaryScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget min,max;
    private String error="";
    public BoundaryScreen(Screen parent){super(Text.literal("日本の境界・EEZ  表示設定"));this.parent=parent;}
    @Override protected void init(){
        int left=width/2-204,top=Math.max(25,height/2-110);ClientPreferences p=BoundaryClient.preferences;
        for(Layer layer:Layer.values()){int i=layer.ordinal(),x=left+(i%2)*208,y=top+22+(i/2)*23;
            String label=switch(layer){case TERRITORIAL->"領海 "+BoundaryClient.territorialDistance+"ブロック";case EEZ->"EEZ "+BoundaryClient.eezDistance+"ブロック";case SHELF->"大陸棚 "+(BoundaryClient.extendedShelf?"延長 "+BoundaryClient.extendedDistance:"延長なし");default->layer.label;};
            addDrawableChild(new CheckboxWidget(x,y,200,20,Text.literal(label).styled(style->style.withColor(BoundaryClient.colors[layer.ordinal()])),p.enabled(layer)){
                @Override public void onPress(){super.onPress();p.visible.put(layer.key,isChecked());p.save();}
            });
        }
        addDrawableChild(new CheckboxWidget(left+208,top+91,200,20,Text.literal("状態を画面左上に表示"),p.hud){@Override public void onPress(){super.onPress();p.hud=isChecked();p.save();}});
        addDrawableChild(new CheckboxWidget(left,top+119,200,20,Text.literal("表示高度を個別に指定"),p.customHeight){@Override public void onPress(){super.onPress();p.customHeight=isChecked();p.save();}});
        min=new TextFieldWidget(textRenderer,left+244,top+119,64,20,Text.literal("表示下限Y"));min.setMaxLength(5);min.setText(String.valueOf(p.customHeight?p.bottomY:BoundaryClient.bottom));addDrawableChild(min);
        max=new TextFieldWidget(textRenderer,left+341,top+119,64,20,Text.literal("表示上限Y"));max.setMaxLength(5);max.setText(String.valueOf(p.customHeight?p.topY:BoundaryClient.top));addDrawableChild(max);
        addDrawableChild(ButtonWidget.builder(Text.literal("すべて表示"),b->{for(Layer l:Layer.values())p.visible.put(l.key,true);p.save();clearAndInit();}).dimensions(left,top+181,125,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("すべて非表示"),b->{for(Layer l:Layer.values())p.visible.put(l.key,false);p.save();clearAndInit();}).dimensions(left+139,top+181,125,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("保存して戻る"),b->close()).dimensions(left+278,top+181,126,20).build());
    }
    @Override public void render(DrawContext d,int mx,int my,float delta){
        renderBackground(d);int left=width/2-204,top=Math.max(25,height/2-110);d.fill(left-12,top-13,left+416,top+213,0xEE101C2B);
        d.drawCenteredTextWithShadow(textRenderer,title,width/2,top,0xFFFFFF);super.render(d,mx,my,delta);
        d.drawTextWithShadow(textRenderer,"下限",left+215,top+125,0xC6D9EA);d.drawTextWithShadow(textRenderer,"上限",left+312,top+125,0xC6D9EA);
        d.drawTextWithShadow(textRenderer,"領空の上限: Y="+BoundaryClient.airTop+"（サーバー設定に追従）",left,top+147,0xA8CEE8);
        d.drawTextWithShadow(textRenderer,error.isEmpty()?"変更はこのクライアントに保存されます。":error,left,top+163,error.isEmpty()?0xA8CEE8:0xFF8888);
    }
    @Override public void close(){
        try{int lo=Integer.parseInt(min.getText()),hi=Integer.parseInt(max.getText());if(lo< -2048||hi>4096||lo>=hi)throw new NumberFormatException();
            var p=BoundaryClient.preferences;p.bottomY=lo;p.topY=hi;p.save();client.setScreen(parent);
        }catch(NumberFormatException e){error="高度は -2048〜4096、下限 < 上限で指定してください。";}
    }
    @Override public boolean shouldPause(){return false;}
}
