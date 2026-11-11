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
  static org.json.JSONObject fake(String method) throws Exception {
    if (method.equals("messages.getConversations")) return new org.json.JSONObject("{\"response\":{\"items\":["
      +"{\"conversation\":{\"peer\":{\"id\":11},\"unread_count\":3},\"last_message\":{\"text\":\"Ты уже видел новый QVK? Выглядит совсем иначе\",\"date\":"+(System.currentTimeMillis()/1000-120)+",\"out\":0,\"from_id\":11}},"
      +"{\"conversation\":{\"peer\":{\"id\":2000000005},\"unread_count\":0,\"chat_settings\":{\"title\":\"Команда QVK\"}},\"last_message\":{\"text\":\"Сборку выложил, проверяйте\",\"date\":"+(System.currentTimeMillis()/1000-4000)+",\"out\":0,\"from_id\":12}},"
      +"{\"conversation\":{\"peer\":{\"id\":12},\"unread_count\":0},\"last_message\":{\"text\":\"\",\"attachments\":[{\"type\":\"audio_message\"}],\"date\":"+(System.currentTimeMillis()/1000-90000)+",\"out\":1,\"from_id\":1}},"
      +"{\"conversation\":{\"peer\":{\"id\":13},\"unread_count\":12},\"last_message\":{\"text\":\"Ок, договорились 👍\",\"date\":"+(System.currentTimeMillis()/1000-200000)+",\"out\":0,\"from_id\":13}}],"
      +"\"profiles\":[{\"id\":11,\"first_name\":\"Анна\",\"last_name\":\"Смирнова\",\"online\":1},{\"id\":12,\"first_name\":\"Legat\",\"last_name\":\"S\"},{\"id\":13,\"first_name\":\"Максим\",\"last_name\":\"Орлов\"}]}}");
    long n=System.currentTimeMillis()/1000;
    return new org.json.JSONObject("{\"response\":{\"items\":["
      +"{\"id\":6,\"from_id\":1,\"out\":1,\"date\":"+(n-20)+",\"text\":\"Да, ставлю прямо сейчас\"},"
      +"{\"id\":5,\"from_id\":11,\"out\":0,\"date\":"+(n-60)+",\"text\":\"Выглядит совсем иначе, попробуй\"},"
      +"{\"id\":4,\"from_id\":11,\"out\":0,\"date\":"+(n-70)+",\"text\":\"Ты уже видел новый QVK?\"},"
      +"{\"id\":3,\"from_id\":1,\"out\":1,\"date\":"+(n-5000)+",\"text\":\"Привет! Как дела?\"},"
      +"{\"id\":2,\"from_id\":1,\"out\":1,\"date\":"+(n-5010)+",\"text\":\"Длинное сообщение, чтобы проверить перенос строк в пузыре и максимальную ширину на экране телефона\"},"
      +"{\"id\":1,\"from_id\":11,\"out\":0,\"date\":"+(n-5100)+",\"text\":\"\",\"attachments\":[{\"type\":\"audio_message\"}]}],\"profiles\":[{\"id\":11,\"first_name\":\"Анна\",\"last_name\":\"Смирнова\"}]}}");
  }
  @Test public void shell() throws Exception {
    Qvk3MusicSource.api=(m,args)->fake(m);
    Activity a=Robolectric.buildActivity(Qvk3ShellActivity.class).setup().get(); for(int i=0;i<4;i++) idle();
    shot(a.getWindow().getDecorView(),"shell_chats");
    Activity c=Robolectric.buildActivity(Qvk3ChatActivity.class,new android.content.Intent().putExtra("peer",11L).putExtra("title","Анна Смирнова")).setup().get(); for(int i=0;i<4;i++) idle();
    shot(c.getWindow().getDecorView(),"shell_chat");
  }
  @Test public void styles() throws Exception {
    Activity a=Robolectric.buildActivity(Qvk3StylesActivity.class).setup().get(); idle();
    shot(a.getWindow().getDecorView(),"styles");
    tall(a,"styles_full");
  }
  @Test public void settings() throws Exception {
    Activity a=Robolectric.buildActivity(Qvk3SettingsActivity.class).setup().get(); idle();
    shot(a.getWindow().getDecorView(),"settings_home");
    tall(a,"settings_home_full");
    java.lang.reflect.Method m=Qvk3SettingsActivity.class.getDeclaredMethod("showSection",int.class); m.setAccessible(true);
    for(int s=0;s<9;s++){ m.invoke(a,s); idle(); tall(a,"settings_sec"+s); }
    android.widget.EditText e=findEdit(a.getWindow().getDecorView()); e.setText("нечит"); idle(); try{Thread.sleep(300);}catch(Exception x){} idle();
    shot(a.getWindow().getDecorView(),"settings_search");
  }
  static android.view.View find(View v, Class<?> c){ if(c.isInstance(v)) return v; if(v instanceof android.view.ViewGroup){ android.view.ViewGroup g=(android.view.ViewGroup)v; for(int i=0;i<g.getChildCount();i++){ android.view.View r=find(g.getChildAt(i),c); if(r!=null) return r;} } return null; }
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
    Activity l=Robolectric.buildActivity(Activity.class).setup().get();
    l.getResources().getConfiguration().orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    l.getResources().getDisplayMetrics().widthPixels=2400; l.getResources().getDisplayMetrics().heightPixels=1080;
    Qvk3PlayerView p4=new Qvk3PlayerView(l); l.setContentView(p4); idle();
    View v=l.getWindow().getDecorView(); v.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY)); v.layout(0,0,2400,1080);
    android.view.View f=find(p4,Qvk3CoverFlowView.class); System.out.println("FLOW h="+f.getHeight()+" w="+f.getWidth()+" density="+l.getResources().getDisplayMetrics().density);
    Bitmap lb=Bitmap.createBitmap(2400,1080,Bitmap.Config.ARGB_8888); v.draw(new Canvas(lb)); new java.io.File(out("x")).getParentFile().mkdirs();
    try(java.io.FileOutputStream o=new java.io.FileOutputStream(out("player_land"))){Bitmap.createScaledBitmap(lb,1200,540,true).compress(Bitmap.CompressFormat.PNG,100,o);}
    a.getSharedPreferences(a.getPackageName()+"_preferences",0).edit().putString(Qvk3MusicSettings.LAYOUT,"classic").commit();
    Qvk3PlayerView p3=new Qvk3PlayerView(a); a.setContentView(p3); idle(); shot(a.getWindow().getDecorView(),"player_classic");
    Activity m2=Robolectric.buildActivity(Qvk3MusicActivity.class).setup().get(); idle(); shot(m2.getWindow().getDecorView(),"music_home2");
  }
}
