package qvk;
import android.app.Activity;import android.graphics.*;import android.view.View;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import re.sova.qmods.*;import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34, qualifiers="w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShotsTest {
  static String out(String n){ return System.getProperty("user.dir")+"/shots/"+n+".png"; }
  static void shot(View v, String name) throws Exception {
    int w=v.getResources().getDisplayMetrics().widthPixels, h=v.getResources().getDisplayMetrics().heightPixels;
    v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
    v.layout(0,0,w,h); Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); v.draw(new Canvas(b));
    Bitmap s=Bitmap.createScaledBitmap(b,w/2,h/2,true);
    new java.io.File(out(name)).getParentFile().mkdirs();
    try(java.io.FileOutputStream o=new java.io.FileOutputStream(out(name))){s.compress(Bitmap.CompressFormat.PNG,100,o);} }
  static void idle(){ for(int i=0;i<5;i++){ org.robolectric.shadows.ShadowLooper.idleMainLooper(); try{Thread.sleep(150);}catch(Exception e){} } }
  static List<Qvk3Track> tracks(){ List<Qvk3Track> l=new ArrayList<>(); String[][] d={{"Кино","Группа крови"},{"Земфира","Хочешь?"},{"Сплин","Выхода нет"},{"Би-2","Полковнику никто не пишет"},{"Мумий Тролль","Владивосток 2000"}};
    for(int i=0;i<d.length;i++) l.add(new Qvk3Track(1,i+1,"",d[i][0],d[i][1],"Альбом "+(i+1),180+i*17,"https://x/"+i,"",i==0,0,"",0)); return l; }
  @Test public void settings() throws Exception {
    Activity a=Robolectric.buildActivity(Qvk3SettingsActivity.class).setup().get(); idle();
    shot(a.getWindow().getDecorView(),"settings_home");
    tall(a,"settings_home_full");
    java.lang.reflect.Method m=Qvk3SettingsActivity.class.getDeclaredMethod("showSection",int.class); m.setAccessible(true);
    for(int s=0;s<9;s++){ m.invoke(a,s); idle(); tall(a,"settings_sec"+s); }
    android.widget.EditText e=findEdit(a.getWindow().getDecorView()); e.setText("нечит"); idle(); try{Thread.sleep(300);}catch(Exception x){} idle();
    shot(a.getWindow().getDecorView(),"settings_search");
  }
  static android.widget.EditText findEdit(View v){ if(v instanceof android.widget.EditText) return (android.widget.EditText)v; if(v instanceof android.view.ViewGroup){ android.view.ViewGroup g=(android.view.ViewGroup)v; for(int i=0;i<g.getChildCount();i++){ android.widget.EditText r=findEdit(g.getChildAt(i)); if(r!=null) return r;} } return null; }
  /** Renders the whole scrollable content (long page). */
  static void tall(Activity a, String name) throws Exception {
    android.widget.ScrollView sv=findScroll(a.getWindow().getDecorView()); View c=sv.getChildAt(0);
    int w=a.getResources().getDisplayMetrics().widthPixels;
    c.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
    int h=Math.min(c.getMeasuredHeight(), 12000); c.layout(0,0,w,h);
    Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(b); cv.drawColor(0xFFF2F3F7); c.draw(cv);
    Bitmap s=Bitmap.createScaledBitmap(b,w/3,h/3,true);
    try(java.io.FileOutputStream o=new java.io.FileOutputStream(out(name))){s.compress(Bitmap.CompressFormat.PNG,100,o);} }
  static android.widget.ScrollView findScroll(View v){ if(v instanceof android.widget.ScrollView) return (android.widget.ScrollView)v; if(v instanceof android.view.ViewGroup){ android.view.ViewGroup g=(android.view.ViewGroup)v; for(int i=0;i<g.getChildCount();i++){ android.widget.ScrollView r=findScroll(g.getChildAt(i)); if(r!=null) return r;} } return null; }
  @Test public void music() throws Exception {
    Activity a=Robolectric.buildActivity(Qvk3MusicActivity.class).setup().get(); idle();
    shot(a.getWindow().getDecorView(),"music_home");
    Qvk3MusicEngine e=Qvk3MusicEngine.get(a);
    List<Qvk3Track> tr=tracks(); List<Qvk3Track> tc=new ArrayList<>(); for(Qvk3Track t:tr) tc.add(new Qvk3Track(t.ownerId,t.id,"",t.artist,t.title,t.album,t.duration,t.url,"https://c/"+t.id,false,0,"",0));
    Object img=Qvk3Images.get(a); java.lang.reflect.Field mf=img.getClass().getDeclaredField("memory"); mf.setAccessible(true); android.util.LruCache<String,Bitmap> mem=(android.util.LruCache<String,Bitmap>)mf.get(img);
    int[] cols={0xFFC0392B,0xFF2E86C1,0xFF17A589,0xFF8E44AD,0xFFD68910};
    for(int i=0;i<5;i++){ Bitmap b=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888); Canvas cv=new Canvas(b); Paint pt=new Paint(); pt.setShader(new LinearGradient(0,0,300,300,cols[i],0xFF111111,Shader.TileMode.CLAMP)); cv.drawRect(0,0,300,300,pt); pt.setShader(null); pt.setColor(0x88FFFFFF); cv.drawCircle(150,150,90,pt); for(int sz:new int[]{144,300,600,900}) mem.put(sz+":https://c/"+(i+1), b); }
    e.queue().set(tc,0);
    Qvk3PlayerView p=new Qvk3PlayerView(a); a.setContentView(p); idle();
    shot(a.getWindow().getDecorView(),"player");

    a.getSharedPreferences(a.getPackageName()+"_preferences",0).edit().putString(Qvk3MusicSettings.LAYOUT,"coverflow").commit();
    Qvk3PlayerView p2=new Qvk3PlayerView(a); a.setContentView(p2); idle(); shot(a.getWindow().getDecorView(),"player_coverflow");
    a.getSharedPreferences(a.getPackageName()+"_preferences",0).edit().putString(Qvk3MusicSettings.LAYOUT,"classic").commit();
    Qvk3PlayerView p3=new Qvk3PlayerView(a); a.setContentView(p3); idle(); shot(a.getWindow().getDecorView(),"player_classic");
    Activity m2=Robolectric.buildActivity(Qvk3MusicActivity.class).setup().get(); idle(); shot(m2.getWindow().getDecorView(),"music_home2");
  }
}
